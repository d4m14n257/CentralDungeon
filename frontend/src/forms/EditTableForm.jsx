import { useCallback, useContext } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ErrorMessage } from "@hookform/error-message"

import { Stack, Autocomplete, TextField, Divider, FormControl, FormHelperText } from "@mui/material";
import { TimeField } from "@mui/x-date-pickers";
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { Textarea } from "./TextArea";
import LoadingButton from '@mui/lab/LoadingButton';

import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { timezone } from "@/constants/constants";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";
import { putter } from "@/api/putter";
import dayjs from "dayjs";

const schema = z.object ({
    name: z.string().min(1, { message: 'Es obligatorio ponerle un nombre a la mesa.' }),
    description: z.string().max(1024, { message: 'No puedes superar la cantidad de 1024 caracteres.' }).nullable(),
    permitted: z.string().max(1024, { message: 'No puedes superar la cantidad de 1024 caracteres.' }).nullable(),
    startdate: z.string().nullable(),
    timezone: z.string().nullable(),
    duration: z.string().nullable(),
})  

export default function EditTableForm (props) {
    const { handleCloseModal, reloadAction, table } = props;
    const { confirm, setMessage } = useContext(Confirm);
    const { handleOpen, setMessage : setStatusMessage, setStatus, setInfo } = useContext(Message);

    const { control, register, handleSubmit, formState: { errors, isSubmitting }} = useForm({
        defaultValues: {
            name: table.name,
            description: table.description,
            permitted: table.permitted,
            startdate: table.startdate,
            timezone: table.timezone,
            duration: table.duration && `0000-00-00 ${table.duration}`
        },
        resolver: zodResolver(schema)
    })

    const onSubmit = useCallback(async (data, event) => {
        try {
            let first = false;
            let count = 0;

            const value = {
                id: table.id,
                name: data.name !== table.name ? data.name : null,
                description: data.description !== table.description ? data.description : null,
                permitted: data.permitted !== table.permitted ? data.permitted : null,
                startdate: data.startdate !== table.startdate ? data.startdate : null,
                timezone: data.timezone !== table.timezone ? data.timezone.substring(3) : null,
                duration: data.duration !== table.duration ? data.duration.substring(11) : null,
            }

            Object.values(value).forEach((element) => {
                if(first) {
                    if(!element) 
                        count++;
                }
                else 
                    first = true;
            })

            if(count == 6) {
                setInfo(true);
                setStatusMessage('No ha habido cambios en los datos.');
                handleOpen();

                throw {message: 'No hay cambios'}
            }

            setMessage('¿Esta seguro de realizar estos cambios?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }
            
            const response = await putter({data: value, url: 'tables/master'});

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setStatusMessage('Datos de la mesa editados con exito.');
                handleOpen();
                handleCloseModal();
                
                if(reloadAction) {
                    await reloadAction();
                }
            }
            else
                throw {message: 'Ha habido un error al momento de editar la mesa.', status: response.status}
        }
        catch (err) {
            if(!err.canceled) {
                setStatus(err.status);
                setMessage(err.message);
                handleOpen();
            }
        }
    }, [])

    return (
        <LocalizationProvider dateAdapter={AdapterDayjs}>
            <form style={modal.content}>
                <Divider />
                <FormControl fullWidth>
                    <TextField
                        required
                        disabled={isSubmitting}
                        error={Boolean(errors.name)}
                        label='Nombre de la mesa'
                        {...register("name")}
                    />
                    <FormHelperText>
                        {errors.name ? 
                            <ErrorMessage errors={errors} name="name"/> :
                            "Ingresa el nombre de la mesa."}
                    </FormHelperText>
                </FormControl>
                <FormControl>
                    <Textarea 
                        disabled={isSubmitting}
                        error={errors.description}
                        minRows={4} sx={modal.formArea}
                        placeholder="Descripción de la mesa"
                        {...register("description")}
                    />
                    <FormHelperText>
                        {errors.description ?
                            <ErrorMessage errors={errors} name="description"/> :
                            "Ingresa la descripcion de tu mesa."}
                        </FormHelperText>
                </FormControl>
                <FormControl>
                    <Textarea 
                        disabled={isSubmitting}
                        error={errors.permitted}
                        minRows={4}
                        sx={modal.formArea}
                        placeholder="Reglas y/o cossas permitidas"
                        {...register("permitted")}/>
                    <FormHelperText>
                        {errors.permitted ? 
                            <ErrorMessage errors={errors} name='permitted'/>: 
                            'Ingresa las reglas que tendra tu mesa.'}
                    </FormHelperText>
                </FormControl>
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <FormControl>
                            <DatePicker
                                disabled={isSubmitting}
                                value={field.value ? dayjs(field.value) : null}
                                minDate={new Date() < table.startdate ? dayjs(new Date()) : dayjs(table.startdate)} 
                                label="Fecha de incio"
                                inputRef={field.ref}
                                onChange={(value) => {
                                    field.onChange(dayjs(value).format('YYYY-MM-DD'))
                                }}
                            />
                            <FormHelperText>Ingresa la fecha de inicio de la sesión.</FormHelperText>
                        </FormControl>
                    }
                    {...register("startdate")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <FormControl>
                            <Autocomplete
                                fullWidth
                                value={field.value}
                                options={timezone}
                                disabled={isSubmitting}
                                isOptionEqualToValue={(option, value) => option === value}
                                onChange={(e, value) => field.onChange(value)}
                                renderInput={(params) => (
                                    <TextField {...params} label="Zona horaria de la mesa" />
                                )}
                            />
                            <FormHelperText>Seleccione su zona horaria.</FormHelperText>
                        </FormControl>}
                    {...register("timezone")}
                />
                <Controller 
                    control={control}
                    render={({ field }) =>
                        <FormControl>
                            <TimeField
                                disabled={isSubmitting}
                                value={field.value ? dayjs(field.value) : null}
                                label="Duracion de sesión"
                                format="HH:mm"
                                InputLabelProps={{
                                    shrink: true,
                                }}
                                onChange={(value) => {
                                    field.onChange(dayjs(value).format('YYYY-MM-DDTHH:mm'));
                                }}
                            />
                            <FormHelperText>Defina el tiempo que tardara en promedio cada sesión.</FormHelperText>
                        </FormControl>
                    }
                    {...register("duration")}
                />
                <Stack direction='row' justifyContent='end'>
                    <LoadingButton 
                        loading={isSubmitting}
                        variant='outlined' 
                        type='submit' 
                        onClick={(event) => handleSubmit(onSubmit)(event)}
                        endIcon={<SendIcon />}
                    >
                        Subir
                    </LoadingButton>
                </Stack>
            </form>
        </LocalizationProvider>
    );
}
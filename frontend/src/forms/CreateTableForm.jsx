import { useState, useReducer, useContext, useCallback } from "react";
import { useForm, Controller } from "react-hook-form";
import { useDebouncedCallback } from "use-debounce";
import { zodResolver } from "@hookform/resolvers/zod";
import { ErrorMessage } from "@hookform/error-message"

import { createFilterOptions } from '@mui/material/Autocomplete';
import { IconButton, List, ListItem, ListItemText, Stack, Autocomplete, TextField, Typography, Divider, FormControl, FormHelperText, Chip, Tooltip } from "@mui/material";
import { TimeField } from "@mui/x-date-pickers";
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { Textarea } from "./TextArea";
import LoadingButton from '@mui/lab/LoadingButton';

import AddCircleIcon from '@mui/icons-material/AddCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { timezone, days } from "@/constants/constants";
import { getter } from "@/api/getter";
import { User } from "@/contexts/UserContext";
import { setter } from "@/api/setter";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";
import dayjs from "dayjs";

/* 
    TODO: Fixed all error on the refs, might be in autocomplete and controller
    TODO: When you closed a form, in general save always in localstorage in order avoid issues with the final user
*/

const filter = createFilterOptions();

const schema = z.object ({
    name: z.string().min(1, { message: 'Es obligatorio ponerle un nombre a la mesa.' }),
    masters: z.object({
        id: z.string(),
        username: z.string(),
        master_type: z.string()
    }).array(),
    description: z.string().max(1024, { message: 'No puedes superar la cantidad de 1024 caracteres.' }),
    permitted: z.string().max(1024, { message: 'No puedes superar la cantidad de 1024 caracteres.' }),
    startdate: z.string().nullable(),
    timezone: z.string().nullable(),
    tags: z.object({
        id: z.string(),
        name: z.string()
    }).array(),
    systems: z.object({
        id: z.string(),
        name: z.string()
    }).array(),
    platforms: z.object({
        id: z.string(),
        name: z.string()
    }).array(),
    duration: z.string().nullable(),
    schedule: z.object({
        day: z.string(),
        hour: z.string().array()
    }).array()
})  

function reducer (state, action) {
    const { type, value, dataArray } = action;

    switch(type) {
        case 'search-masters': {
            return {
                ...state,
                masters: dataArray,
                inputMasters: value
            }
        }
        case 'search-tags': {
            return {
                ...state,
                tags: dataArray,
                inputTags: value
            }
        }
        case 'search-systems': {
            return {
                ...state,
                systems: dataArray,
                inputSystems: value
            }
        }
        case 'search-platforms': {
            return {
                ...state,
                platforms: dataArray,
                inputPlatforms: value
            }
        }
    }
}

export default function CreateTableForm (props) {
    const { handleCloseModal, reloadAction } = props;
    const { confirm, setMessage } = useContext(Confirm);
    const { username, id } = useContext(User);
    const { handleOpen, setMessage : setStatusMessage, setStatus } = useContext(Message);
    const [ scheduleInfo, setScheduleInfo ] = useState({
        days: [],
        time: '0000-00-00 02:00:00',
        list: []
    });
    const [data, dispatch] = useReducer(reducer, {
        masters: [],
        inputMasters: '',
        tags: [],
        inputTags: '',
        systems: [],
        inputSystems: '',
        platforms: [],
        inputPlatforms: '',
        scheduleHash: {
            Monday: [],
            Thursday: [],
            Wednesday: [],
            Tuesday: [],
            Friday: [],
            Saturday: [],
            Sunday: []
        },
    });

    const { control, register, handleSubmit, setValue, formState: { errors, isSubmitting }} = useForm({
        defaultValues: {
            name: '',
            masters: [{
                id: id,
                username: username,
                master_type: 'Owner'
            }],
            tags: [],
            systems: [],
            platforms: [],
            schedule: [],
            startdate: null,
            duration: null,
            timezone: null
        },
        resolver: zodResolver(schema)
    })

    const handleMasterList = useDebouncedCallback(async (event, value) => {
        let masters = [] ;

        if(value.length > 3) {
            masters = await getter({others: value, url: 'users/masters'});

            if(!masters.status)
                masters = masters.users_master
            else
                masters = []
        }

        dispatch({type: 'search-masters', value: value, dataArray: masters})
    }, 300)
    
    const handleCataloguesList = useDebouncedCallback(async (event, value, url, type) => {
        let data = [];

        if(value.length > 0) {
            data = await getter({others: value, url: url}).then((response) => {
                if(response.status = 200)
                    return response.catalogues;
                else
                    data = [];
            });
        }

        dispatch({type: type, value: value, dataArray: data});
    }, 300)

    const handleCreateShedule = (field) => {
        const time = typeof scheduleInfo.time == 'string' ? '02:00' : scheduleInfo.time.format('HH:mm')

        if(scheduleInfo.days.length > 0) {
            let schedule = [];
            
            scheduleInfo.days.map((day) => {
                if (!data.scheduleHash[day].includes(time)) {
                    data.scheduleHash[day].push(time);
                }
            });

            for(const day in data.scheduleHash) {
                if(data.scheduleHash[day].length > 0 )
                    schedule.push({
                        day: day,
                        hour: data.scheduleHash[day]
                    })
            }
            
            field.onChange(schedule);   

            setScheduleInfo({
                days: [],
                time: '0000-00-00 02:00:00',
                list: schedule
            }) 
        }
    }

    const handleDeleteSchedule = (weekday) => {
        let schedule = [];
        data.scheduleHash[weekday.day] = [];

        for(const day in data.scheduleHash) {
            if(data.scheduleHash[day].length > 0 )
                schedule.push({
                    name: day,
                    hour: data.scheduleHash[day]
                })
        }
        
        setValue('schedule', schedule);
        setScheduleInfo({
            ...scheduleInfo,
            list: schedule
        })
    }

    const onSubmit = useCallback(async (data, event) => {
        try {
            setMessage('¿Esta seguro de crear esta mesa?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }
            
            const response = await setter({
                data: {
                    ...data,
                    description: data.description.length > 0 ? data.description : null,
                    permitted: data.permitted.length > 0 ? data.permitted : null,
                    tags: data.tags.length > 0 ? data.tags : null,
                    systems: data.systems.length > 0 ? data.systems : null,
                    platforms: data.platforms.length > 0 ? data.platforms : null,
                    schedule: data.schedule.length > 0 ? data.schedule : null,
                    timezone: data.timezone ? data.timezone.substring(3) : data.timezone
                }, 
                url: 'tables/master'
            });

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setStatusMessage('Mesa creada con exito.');
                handleCloseModal();
                handleOpen();
                
                if(reloadAction)
                    await reloadAction();
            }
            else {
                throw {message: 'Ha habido un error al crear la mesa.', status: response.status}
                
            }
        }
        catch (err) {
            if(!err.canceled) {
                setStatus(err.status);
                setStatusMessage(err.message);
                handleOpen();
            }
        }
    }, [])

    return (
        <LocalizationProvider dateAdapter={AdapterDayjs}>
            <form style={modal.content}>
                <Stack spacing={0.3} flexDirection='column'>
                    <Typography>Datos importantes</Typography>
                    <Typography variant='caption'>Son datos obligatoios.</Typography>
                    <Divider />
                </Stack>
                <FormControl fullWidth>
                    <TextField
                        disabled={isSubmitting}
                        error={Boolean(errors.name)}
                        required
                        label='Nombre de la mesa'
                        {...register("name")}
                    />
                    <FormHelperText>
                        {errors.name ? 
                            <ErrorMessage errors={errors} name="name"/> :
                            "Ingresa el nombre de la mesa."}
                    </FormHelperText>
                </FormControl>
                <Controller
                    control={control}
                    render={({ field }) => 
                        <FormControl>
                            <Autocomplete
                                fullWidth
                                multiple
                                disabled={isSubmitting}
                                limitTags={3}
                                options={data.masters}
                                filterSelectedOptions
                                isOptionEqualToValue={(option, value) => option.id == value.id}
                                getOptionLabel={(option) => typeof option === 'string' ? option : option.username}
                                onInputChange={(event, newValue) => {handleMasterList(event, newValue)}}
                                renderInput={(params) => (<TextField {...params} label="Masters en la mesa" required/>)}
                                renderTags={(tagValue, getTagProps) =>
                                    tagValue.map((option, index) => (
                                    <Chip
                                        label={option.username}
                                        {...getTagProps({ index })}
                                        disabled={option.username === username}
                                    />
                                    ))
                                }
                                value={field.value}
                                onChange={(e, value) => field.onChange(value)}
                            />
                            <FormHelperText>Ingresa los masters que estaran en la mesa.</FormHelperText>
                        </FormControl>
                    }
                    name='masters'
                />
                <Stack spacing={0.3} flexDirection='column'>
                    <Typography>Datos generales</Typography>
                    <Typography variant='caption'>Lo siguiente es opcional, en caso de ser necesario puedes continuar.</Typography>
                    <Divider />
                </Stack>
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
                                minDate={dayjs(new Date())} 
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
                            <Autocomplete
                                fullWidth
                                multiple
                                limitTags={8}
                                disabled={isSubmitting}
                                options={data.tags}
                                filterSelectedOptions
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                                onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'tags', 'search-tags')}}
                                renderInput={(params) => (<TextField {...params} label="Tags de la mesa" />)}
                                value={field.value}
                                onChange={(e, value) => field.onChange(value)}
                                filterOptions={(options, params) => {
                                    const filtered = filter(options, params)
                                    const { inputValue } = params;
                                
                                    const isExisting = options.some((option) => inputValue === option.name);
                                    if (inputValue !== '' && !isExisting) {

                                        filtered.push({
                                            inputValue,
                                            name: `Agregar "${inputValue}"`,
                                            id: `${inputValue}`
                                        });
                                    }

                                    return filtered;
                                }}
                            />
                            <FormHelperText>Seleccione generos que represente de que tratara la mesa. En caso de que no exista puedes agregarlo.</FormHelperText>
                        </FormControl>
                    }
                    {...register("tags")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <FormControl>
                            <Autocomplete
                                fullWidth
                                multiple
                                limitTags={3}
                                options={data.systems}
                                disabled={isSubmitting}
                                filterSelectedOptions
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                                onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'systems', 'search-systems')}}
                                renderInput={(params) => (<TextField {...params} label="Sistemas de juego." />)}
                                value={field.value}
                                onChange={(e, value) => field.onChange(value)}
                                filterOptions={(options, params) => {
                                    const filtered = filter(options, params)
                                    const { inputValue } = params;
                                
                                    const isExisting = options.some((option) => inputValue === option.name);
                                    if (inputValue !== '' && !isExisting) {

                                        filtered.push({
                                            inputValue,
                                            name: `Agregar "${inputValue}"`,
                                            id: `${inputValue}`
                                        });
                                    }

                                    return filtered;
                                }}
                            />
                            <FormHelperText>Seleccion los sistemas de juego que va a utilizar. En caso de no estar, puede agregarlo.</FormHelperText>
                        </FormControl>
                    }
                    {...register("systems")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <FormControl>
                            <Autocomplete
                                fullWidth
                                multiple
                                disabled={isSubmitting}
                                limitTags={3}
                                options={data.platforms}
                                filterSelectedOptions
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                                onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'platforms', 'search-platforms')}}
                                renderInput={(params) => (<TextField {...params} label="Plataformas de la mesa." />)}
                                value={field.value}
                                onChange={(e, value) => field.onChange(value)}
                                filterOptions={(options, params) => {
                                    const filtered = filter(options, params)
                                    const { inputValue } = params;
                                
                                    const isExisting = options.some((option) => inputValue === option.name);
                                    if (inputValue !== '' && !isExisting) {

                                        filtered.push({
                                            inputValue,
                                            name: `Agregar "${inputValue}"`,
                                            id: `${inputValue}`
                                        });
                                    }

                                    return filtered;
                                }}
                            />
                            <FormHelperText>Seleccion en que plataformas va estar disponible la mesa. En caso de no estar puede agregarlo.</FormHelperText>
                        </FormControl>
                    }
                    {...register("platforms")}
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
                <Stack spacing={0.3} flexDirection='column'>
                    <Typography> Horario </Typography>
                    <Typography variant='caption'>Aqui puede definir los dias y la hora a la que sera las sesiones.</Typography>
                    <Divider />
                </Stack>
                <Controller 
                    control={control}
                    render={({ field }) => (
                            <Stack direction='row' spacing={3} alignItems='center'>
                                <Stack direction={{lg : 'row', xs: 'column'}} spacing={2} sx={{width: '100%'}}>
                                    <FormControl
                                        sx={{width: {lg: '400px'}}}
                                    >
                                        <Autocomplete
                                            freeSolo
                                            disabled={isSubmitting}
                                            multiple
                                            filterSelectedOptions
                                            options={days}
                                            renderInput={(params) => (
                                                <TextField {...params} label="Dias de la semana" />
                                            )}
                                            value={scheduleInfo.days}
                                            onChange={(e, value) => {
                                                setScheduleInfo({...scheduleInfo, days: value})
                                            }}
                                        />
                                        <FormHelperText>Seleccione el dia de la semana.</FormHelperText>
                                    </FormControl>
                                    <FormControl
                                            sx={{width: {lg: '200px'}}}
                                    >
                                        <TimeField
                                            disabled={isSubmitting}
                                            label="Hora de inicio"
                                            format="HH:mm"
                                            value={dayjs(scheduleInfo.time)}
                                            onChange={(value) => {
                                                setScheduleInfo({...scheduleInfo, time: value})
                                            }}
                                        />
                                        <FormHelperText>Ponga la hora de incio. Sistema de 24 horas</FormHelperText>
                                    </FormControl>
                                    <Tooltip
                                        title='Agregar al horario'
                                    >
                                        <IconButton 
                                            sx={{...modal.icon, 
                                                    display: {lg: 'inline-flex', xs: 'none'}
                                                }} 
                                            onClick={() => {
                                                handleCreateShedule(field);
                                            }}
                                        >
                                            <AddCircleIcon fontSize="inherit"/>
                                        </IconButton>
                                    </Tooltip>
                                </Stack>
                                <Tooltip
                                        title='Agregar al horario'
                                    >
                                        <IconButton 
                                            sx={{...modal.icon, 
                                                    display: {lg: 'none', xs: 'inline-flex'}
                                                }} 
                                            onClick={() => {
                                                handleCreateShedule(field);
                                            }}
                                        >
                                            <AddCircleIcon fontSize="inherit"/>
                                        </IconButton>
                                    </Tooltip>
                            </Stack>
                        )}
                    {...register("schedule")}
                />
                <List>
                    {scheduleInfo.list.map((weekday) => (
                        <>
                            <Divider variant='middle'/>
                            <ListItem
                                key={weekday.day}
                                sx={{paddingX: 3}}
                                secondaryAction={
                                    <IconButton 
                                        color="error"
                                        size="large" 
                                        edge="end" 
                                        onClick={() => handleDeleteSchedule(weekday)}>
                                        <DeleteIcon />
                                    </IconButton>
                                }
                            >
                                <ListItemText
                                    primary={weekday.day}
                                    secondary={weekday.hour.join(', ')}
                                />
                            </ListItem>
                        </>
                    ))}
                </List>
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
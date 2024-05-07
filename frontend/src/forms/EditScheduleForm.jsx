import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Autocomplete, FormHelperText, Stack, FormControl, Tooltip, IconButton, TextField, ListItem, ListItemText, List } from "@mui/material";
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider, TimeField } from "@mui/x-date-pickers";

import AddCircleIcon from '@mui/icons-material/AddCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { LoadingButton } from "@mui/lab";
import { days } from "@/constants/constants";
import dayjs from "dayjs";

const schema = z.object ({
    schedule: z.object({
        day: z.string(),
        hour: z.string().array()
    }).array()
})

export default function EditScheduleForm (props) {
    const { handleCloseModal, reloadAction, schedule, table_id} = props;
    const [ scheduleInfo, setScheduleInfo ] = useState({
        days: [],
        time: '0000-00-00 02:00:00',
        list: []
    });


    const { control, register, handleSubmit, setValue, formState: { errors, isSubmitting }} = useForm({
        defaultValues: {
            schedule: schedule,
        },
        resolver: zodResolver(schema)
    })

    return (
        <LocalizationProvider dateAdapter={AdapterDayjs}>
            <form style={modal.content}>
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
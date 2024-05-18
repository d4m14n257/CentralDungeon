import { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Autocomplete, FormHelperText, Stack, FormControl, Tooltip, IconButton, TextField, ListItem, ListItemText, List, Divider, Box } from "@mui/material";
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider, TimeField } from "@mui/x-date-pickers";

import AddCircleIcon from '@mui/icons-material/AddCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { LoadingButton } from "@mui/lab";
import { days } from "@/constants/constants";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";
import { putter } from "@/api/putter";
import dayjs from "dayjs";

const schema = z.object ({
    schedule: z.object({
        day: z.string(),
        hour: z.string().array()
    }).array()
})

export default function EditScheduleForm (props) {
    const { handleCloseModal, reloadAction, schedule, table_id} = props;
    const { confirm, setMessage } = useContext(Confirm)
    const { handleOpen, setMessage : setStatusMessage, setStatus, setInfo } = useContext(Message)
    const [ initial, setInitial ] = useState([]);
    const [ scheduleInfo, setScheduleInfo ] = useState({
        days: [],
        time: '0000-00-00 14:00:00',
        list: []
    });

    const scheduleHash = useMemo(() => ({
        Monday: [],
        Thursday: [],
        Wednesday: [],
        Tuesday: [],
        Friday: [],
        Saturday: [],
        Sunday: []
    }), [])

    useEffect(() => {
        const first = [];

        schedule.map((weekday) => {
            scheduleHash[weekday.day].push(weekday.hour);
        })

        for(const day in scheduleHash) {
            if(scheduleHash[day].length > 0) {
                first.push({
                    day: day,
                    hour: scheduleHash[day]
                })
            }
        }

        const firstCopyForInitial = JSON.parse(JSON.stringify(first));
        const firstCopyForList = JSON.parse(JSON.stringify(first));
        
        setValue('schedule', first);
        setInitial(firstCopyForInitial)
        setScheduleInfo({
            days: [],
            time: '0000-00-00 14:00:00',
            list: firstCopyForList
        }) 
    }, [])

    const { control, register, handleSubmit, setValue, formState: { isSubmitting }} = useForm({
        resolver: zodResolver(schema)
    })

    const handleCreateShedule = (field) => {
        const time = typeof scheduleInfo.time == 'string' ? '14:00' : scheduleInfo.time.format('HH:mm')

        if(scheduleInfo.days.length > 0) {
            let schedule = [];
            
            scheduleInfo.days.map((day) => {
                if (!scheduleHash[day].includes(time)) {
                    scheduleHash[day].push(time);
                }
            });

            for(const day in scheduleHash) {
                if(scheduleHash[day].length > 0) {
                    schedule.push({
                        day: day,
                        hour: scheduleHash[day].sort()
                    })
                }
            }
            
            field.onChange(schedule);   

            setScheduleInfo({
                ...scheduleInfo,
                days: [],
                time: '0000-00-00 14:00:00',
                list: schedule
            }) 
            
        }
    }

    const handleDeleteSchedule = (weekday) => {
        let schedule = [];
        scheduleHash[weekday.day] = [];

        for(const day in scheduleHash) {
            if(scheduleHash[day].length > 0 )
                schedule.push({
                    day: day,
                    hour: scheduleHash[day]
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
            if(initial.length === scheduleInfo.list.length) {
                let count = 0;

                for(const first of initial) {
                    const originalHours = first.hour.join('');

                    for(const list of scheduleInfo.list) {
                        const listHours = list.hour.join('');

                        if(list.day === first.day) {
                            if(originalHours == listHours) {
                                count++
                                break;
                            }
                        }
                    }
                }

                if(count === initial.length)
                    throw {message: 'No ha habido cambios en el horario', info: true}
            }
            
            setMessage('¿Estas seguro de establecer este horario?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}})
            }

            const response = await putter({
                data: {
                    update_schedule: data.schedulen,
                    original: initial
                },
                id: table_id,
                url: 'tables/schedule'
            })

            console.log(response)

            if(response.status >= 200 && Response.status <= 299) {

                if(reloadAction)
                    await reloadAction();

                throw {message: 'Horario editado con exito.', status: response.status};
            }
            else {
                throw {message: 'Ha habido un error al momento de establecer el horario.', status: response.status}
            }
        }
        catch(err) {
            if(err.info) {
                setInfo(err.info);
                setStatusMessage(err.message);
                handleOpen();
            }
            else if(!err.canceled) {
                setStatus(err.status);
                setStatusMessage(err.message);
                handleOpen();
            }
        }
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
                        <Box
                            key={weekday.day}
                        >
                            <Divider variant='middle'/>
                            <ListItem
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
                                    secondary={weekday.hour.join(' - ')}
                                />
                            </ListItem>
                        </Box>
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
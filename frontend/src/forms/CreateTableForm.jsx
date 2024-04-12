import { useState, useReducer } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import { createFilterOptions } from '@mui/material/Autocomplete';
import { IconButton, List, ListItem, ListItemText, Stack, Button, FormControlLabel, Switch, Autocomplete, TextField, Typography, Divider } from "@mui/material";
import { TimeField } from "@mui/x-date-pickers";
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { Textarea } from "./TextArea";
import UploadButton from "./UploadButton";

import AddCircleIcon from '@mui/icons-material/AddCircle';
import DeleteIcon from '@mui/icons-material/Delete';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { timezone, days } from "@/helper/constants";
import { getter } from "@/api/getter";
import dayjs from "dayjs";

/* TODO: Cambiar la forma del horario como en la imagen del discord 
    Indexar catalogos que sea referente entre si.
    Poder eliminar la tag principal por una nueva, en caso de ser
    nesesario - O en caso de cambiar el principal por otro principal
*/

const filter = createFilterOptions();

const schema = z.object ({
    name: z.string(),
    masters: z.object({
        id: z.string(),
        username: z.string()
    }).array(),
    description: z.string().max(1024),
    permitted: z.string().max(1024),
    startdate: z.object({}),
    timezone: z.string(),
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
    duration: z.object({}),
    requeriments: z.string().max(1024),
    files: z.object({
        name: z.string(),
        file: z.string()
    }).array(),
    schedule: z.object({
        name: z.string(),
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

export default function CreateTableForm () {
    const [requerimets, setRequeriments] = useState(false);
    const [files, setFiles] = useState(false);
    const [scheduleInfo, setScheduleInfo] = useState({
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
        }
    });

    const { control, register, handleSubmit, setError, setValue, formState: { errors, isSubmitting }} = useForm({
        defaultValues: {
            name: "Prueba de un default",
            masters: [{
                id: "1",
                username: "Teshynil"
            }],
            tags: [],
            systems: [],
            platforms: [],
            schedule: [],
            startdate: null
        },
        resolver: zodResolver(schema)
    })

    const handleChangeRequeriments = () => {
        setRequeriments(!requerimets)
    }

    const handleChangeFiles = () => {
        setFiles(!files)
    }

    const handleMasterList = async (value) => {
        let masters = [] ;

        if(value.length > 3) {
            masters = await getter(value, 'users/masters');

            if(!masters.status)
                masters = masters.users_master
            else
                masters = []
        }

        dispatch({type: 'search-masters', value: value, dataArray: masters})
    }
    
    const handleCataloguesList = async (event, value, url, type) => {
        if(event.nativeEvent.inputType == 'insertText') {
            let data = [];

            if(value.length > 0) {
                data = await getter(value, url);

                if(data.status)
                    data = [];
            }

            dispatch({type: type, value: value, dataArray: data});
        }
    }

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
                        name: day,
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

    const handleDeleteSchedule = (day) => {
        let schedule = [];
        data.scheduleHash[day.name] = [];

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

    const handleFilesAdd = (event, field) => {

        console.log(event.target.value)
    }

    const onSubmit = async (data) => {
        console.log(data)
        try {
            await new Promise((resolve) => setTimeout(resolve, 1000));
            console.log(data);
        } catch (error) {
            console.log(error)
            setError("root", {
                message: "This email is already taken",
            });
        }
    }
    
    return (
        <LocalizationProvider dateAdapter={AdapterDayjs}>
            <form style={modal.content} onSubmit={handleSubmit(onSubmit)}>
                <Typography>General</Typography>
                <Divider />
                <TextField
                    label='Nombre de la mesa'
                    {...register("name")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <Autocomplete
                            multiple
                            limitTags={3}
                            options={data.masters}
                            filterSelectedOptions
                            isOptionEqualToValue={(option, value) => option.id == value.id}
                            getOptionLabel={(option) => typeof option === 'string' ? option : option.username}
                            onInputChange={(event, newValue) => {handleMasterList(newValue)}}
                            renderInput={(params) => (<TextField {...params} label="Masters en la mesa"/>)}
                            sx={{ width: '500px' }}
                            value={field.value}
                            onChange={(e, value) => field.onChange(value)}
                        />
                    }
                    {...register("masters")}
                />
                <Textarea minRows={4} sx={modal.formArea} placeholder="Descripción de la mesa" {...register("description")}/>
                <Textarea minRows={4} sx={modal.formArea} placeholder="Reglas y/o cossas permitidas" {...register("permitted")}/>
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <DateTimePicker 
                            ampm={false}
                            label="Fecha de incio"
                            value={field.value ? dayjs(field.value) : dayjs('')}
                            inputRef={field.ref}
                            onChange={(value) => {
                                field.onChange(value)
                            }}
                        />
                    }
                    {...register("startdate")}
                />
                <Autocomplete
                    options={timezone}
                    renderInput={(params) => (
                        <TextField {...params} label="Zona horaria de la mesa" />
                    )}
                    sx={{ width: '500px' }}
                    {...register("timezone")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <Autocomplete
                            multiple
                            limitTags={8}
                            options={data.tags}
                            filterSelectedOptions
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                            onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'tags', 'search-tags')}}
                            renderInput={(params) => (<TextField {...params} label="Tags de la mesa" />)}
                            sx={{ width: '500px' }}
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
                    }
                    {...register("tags")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <Autocomplete
                            multiple
                            limitTags={3}
                            options={data.systems}
                            filterSelectedOptions
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                            onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'systems', 'search-systems')}}
                            renderInput={(params) => (<TextField {...params} label="Sistemas de juego." />)}
                            sx={{ width: '500px' }}
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
                    }
                    {...register("systems")}
                />
                <Controller 
                    control={control}
                    render={({ field }) => 
                        <Autocomplete
                            multiple
                            limitTags={3}
                            options={data.platforms}
                            filterSelectedOptions
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                            onInputChange={(event, newValue) => {handleCataloguesList(event, newValue, 'platforms', 'search-platforms')}}
                            renderInput={(params) => (<TextField {...params} label="Plataformas de la mesa." />)}
                            sx={{ width: '500px' }}
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
                    }
                    {...register("platforms")}
                />
                <Controller 
                    control={control}
                    render={({ field }) =>
                        <TimeField
                            label="Duracion de sesión"
                            format="HH:mm"
                            InputLabelProps={{
                                shrink: true,
                            }}
                            value={dayjs(field.value)}
                            onChange={(value) => {
                                field.onChange(value);
                            }}
                        />
                    }
                    {...register("duration")}
                />
                {requerimets && <Textarea minRows={3} sx={modal.formArea} placeholder="Requerimientos de la mesa" {...register('requeriments')}/>}
                {files && 
                <Stack>
                    <Controller 
                        control={control}
                        render={({ field }) => {
                            return (
                                <UploadButton onChange={(event) => handleFilesAdd(event, field)}/>
                            );
                        }}
                        {...register('files')}
                    />
                    <List></List>
                </Stack>}
                <Controller 
                    control={control}
                    render={({ field }) => {
                        return (
                            <>
                                <Typography> Horario </Typography>
                                <Divider />
                                <Stack direction='row' spacing={3}>
                                    <Autocomplete 
                                        freeSolo
                                        multiple
                                        filterSelectedOptions
                                        options={days}
                                        renderInput={(params) => (
                                            <TextField {...params} label="Dias de la semana" />
                                        )}
                                        sx={{ width: '500px' }}
                                        value={scheduleInfo.days}
                                        onChange={(e, value) => {
                                            setScheduleInfo({...scheduleInfo, days: value})
                                        }}
                                    />
                                    <TimeField
                                        label="Hora de inicio"
                                        format="HH:mm"
                                        value={dayjs(scheduleInfo.time)}
                                        onChange={(value) => {
                                            setScheduleInfo({...scheduleInfo, time: value})
                                        }}
                                    />
                                    <IconButton sx={modal.icon} onClick={() => {
                                        handleCreateShedule(field);
                                    }}>
                                        <AddCircleIcon fontSize="inherit"/>
                                    </IconButton>
                                </Stack>
                            </>
                        );
                    }}
                    {...register("schedule")}
                />
                <List>
                    {scheduleInfo.list.map((day) => (
                        <ListItem
                            sx={{paddingX: 3}}
                            secondaryAction={
                                <IconButton size="large" edge="end" onClick={() => handleDeleteSchedule(day)}>
                                    <DeleteIcon />
                                </IconButton>
                            }
                        >
                        <ListItemText
                            primary={day.name}
                            secondary={day.hour.join(', ')}
                        />
                    </ListItem>
                    ))}
                </List>
                <Button onClick={() => {console.log()}}>
                    dasdsa
                </Button>
                <Stack direction='row' justifyContent='space-between'>
                    <Stack direction="row" justifyContent='space-around' spacing={7}>
                        <FormControlLabel control={<Switch onChange={handleChangeRequeriments}/>} label="Requerimientos" />
                        <FormControlLabel control={<Switch onChange={handleChangeFiles} />} label="Archivos" />
                    </Stack>
                    <Button disabled={isSubmitting} variant='outlined' type='submit'>
                        {isSubmitting ? "Loading..." : "Submit"}
                    </Button>
                </Stack>
                {errors.root && <div className="text-red-500">{errors.root.message}</div>}
            </form>
        </LocalizationProvider>
    );
}
import { useState } from "react";
import { useForm } from "react-hook-form";

import { z } from "zod";

import TextField from '@mui/material/TextField';
import Autocomplete from '@mui/material/Autocomplete';
import Switch from '@mui/material/Switch';
import FormGroup from '@mui/material/FormGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import UploadButton from "./UploadButton";
import Checkbox from '@mui/material/Checkbox';
import { Textarea } from "./TextArea";

/* TODO: Cambiar la forma del horario como en la imagen del discord 
    Indexar catalogos que sea referente entre si.
    Poder eliminar la tag principal por una nueva, en caso de ser
    nesesario - O en caso de cambiar el principal por otro principal
*/

export default function CreateTableForm () {
    const [requerimets, setRequeriments] = useState(false);
    const [files, setFiles] = useState(false);

    const schema = z.object ({
        name: z.string(),
        master: z.string().array()
    })

    const handleChangeRequeriments = () => {
        setRequeriments(!requerimets)
    }

    const handleChangeFiles = () => {
        setFiles(!files)
    }
    
    return (
        <>
            <TextField
                required
                id="outlined-required"
                label='Nombre de la mesa'
            />
            <Autocomplete
                limitTags={2}
                options={["Master 1", "Master 2", "Master 3"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={"Master 1"}
                renderInput={(params) => (
                    <TextField {...params} label="Masters en la mesa" />
                )}
                sx={{ width: '500px' }}
            />
            <Textarea minRows={3} sx={{maxWidth: 786}} placeholder="Descripción de la mesa"/>
            <Textarea minRows={3} sx={{maxWidth: 786}} placeholder="Reglas y/o cossas permitidas"/>
            <TextField
                required
                id="outlined-required"
                type='date'
                InputLabelProps={{
                    shrink: true,
                }}
                label="Fecha de incio"
            />
            <Autocomplete
                options={["UTC-06:00", "UTC-07:00", "UTC-08:00"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={"UTC-06:00"}
                renderInput={(params) => (
                    <TextField {...params} label="Zona horaria de la mesa" />
                )}
                sx={{ width: '500px' }}
            />
            <Autocomplete
                multiple
                limitTags={2}
                options={["Gore", "Safeword", "D&D"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={["Gore", "Safeword", "D&D"]}
                renderInput={(params) => (
                    <TextField {...params} label="Tags de la mesa" placeholder="Generos" />
                )}
                sx={{ width: '500px' }}
            />
            <Autocomplete
                limitTags={2}
                options={["Sistema 1", "Sistema 2", "Sistema 3"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={"Sistema 1"}
                renderInput={(params) => (
                    <TextField {...params} label="Sistema de la mesa" placeholder="Sistema" />
                )}
                sx={{ width: '500px' }}
            />
            <Autocomplete
                limitTags={2}
                options={["Roll&20", "NosePlataformas"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={"Roll&20"}
                renderInput={(params) => (
                    <TextField {...params} label="Plataforma de la mesa" />
                )}
                sx={{ width: '500px' }}
            />
            <TextField
                label="Duración de sesión"
                type="time"
                InputLabelProps={{
                    shrink: true,
                }}
            />
             <FormGroup>
                <FormControlLabel control={<Switch onChange={handleChangeRequeriments}/>} label="Requerimientos" />
                <FormControlLabel control={<Switch onChange={handleChangeFiles} />} label="Archivos" />
            </FormGroup>
            {requerimets && <Textarea minRows={3} sx={{maxWidth: 786}} placeholder="Requerimientos de la mesa"/>}
            {files && <UploadButton />}
            <FormGroup>
                <FormControlLabel control={<Checkbox />} label="Lunes" />
                <FormControlLabel control={<Checkbox />} label="Martes" />
                <FormControlLabel control={<Checkbox />} label="Miercoles" />
                <FormControlLabel control={<Checkbox />} label="Jueves" />
                <FormControlLabel control={<Checkbox />} label="Viernes" />
                <FormControlLabel control={<Checkbox />} label="Sabado" />
                <FormControlLabel control={<Checkbox />} label="Domingo" />
            </FormGroup>
        </>
    );
}
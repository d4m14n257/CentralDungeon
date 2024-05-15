import { useCallback, useEffect, useContext, useState, useRef } from "react";
import { useForm, Controller } from "react-hook-form";
import { Divider, FormControl, FormHelperText, IconButton, List, ListItem, ListItemText, Stack, Typography, Collapse, Box, Button } from "@mui/material";
import { LoadingButton } from "@mui/lab";
import { FileUploader } from "react-drag-drop-files";
import { zodResolver } from "@hookform/resolvers/zod";

import SendIcon from '@mui/icons-material/Send';
import DeleteIcon from '@mui/icons-material/Delete';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";
import { getter } from "@/api/getter";
import { User } from "@/contexts/UserContext";
import { Files } from "@/normalize/models";
import { global } from "@/styles/global";
import { setterFiles } from "@/api/setterFiles";

const schema = z.object ({
    files: z.object({
        id: z.string().default('NoID').nullable(),
        name: z.string(),
        type: z.string(),
        size: z.number()
    }).array()
}) 

export default function EditFilesForm (props) {
    const { table_id, files, reloadAction, handleCloseModal } = props;
    const { confirm, setMessage } = useContext(Confirm);
    const { handleOpen, setMessage : setStatusMessage, setStatus, setInfo } = useContext(Message);
    const { id } = useContext(User)
    const [listFiles, setListFiles] = useState(files);
    const [publicFiles, setPublicFiles] = useState([]); 
    const [privateFiles, setPrivateFiles] = useState([]);
    const [openPublic, setOpenPublic] = useState(false);
    const [openPrivate, setOpenPrivate] = useState(false);
    const uploadFiles = useRef(null);
    const reuseFiles = useRef([]);

    const fileTypes = ['png', 'jpg', 'jpeg', 'pdf', 'docx', 'doc']

    const { control, register, handleSubmit, setValue, formState: { isSubmitting }} = useForm({
        defaultValues: {
            files: files
        },
        resolver: zodResolver(schema)
    })
    
    useEffect(() => {
        const getFiles = async () => {
            const result = await getter({id: table_id, others: id, url: 'files/tables/preparation' });

            if(result.status == 200) {
                const data = {
                    public_files: Files(result.public_files),
                    private_files: Files(result.private_files)
                }

                setPublicFiles(data.public_files);
                setPrivateFiles(data.private_files);
            }
        }
        
        getFiles();
    }, []);

    const handleAddFile = (file, field) => {
        const newListFile = [...listFiles];
        const length = file.length;

        for(let i = 0; i < length; i++) {
            console.log(typeof file[i])
            const add = {
                name: file[i].name,
                type: file[i].type,
                size: file[i].size
            };

            newListFile.push(add);
        }

        uploadFiles.current = uploadFiles.current ? [...uploadFiles.current, ...file] : [...file];
        setListFiles(newListFile);
        field.onChange(newListFile);
    };

    const handleDeleteFile = (index) => {
        const newListFile = [...listFiles]
        
        if(newListFile[index].id) {
            for(let i = 0; i < reuseFiles.current.length; i++) {
                if(reuseFiles.current[i].id == newListFile[index].id) {
                    reuseFiles.current.splice(i, 1);
                    
                    switch(newListFile[index].place) {
                        case 'Private': {
                            setPrivateFiles([...privateFiles, newListFile[index]]);
                            break;
                        }
                        case 'Public': {
                            setPublicFiles([...publicFiles, newListFile[index]]);
                            break;
                        }
                    }

                    break;
                }
            }
        }
        else
            uploadFiles.current.splice(index, 1);

        newListFile.splice(index, 1);
        setListFiles(newListFile);
        setValue('files', newListFile);
    }

    const handleSelectCloudFiles = (file, list, type) => {
        const newListFile = [...listFiles];
        const length = list.length;

        reuseFiles.current = reuseFiles.current.length ? [...reuseFiles.current, file] : [file];
        newListFile.push({...file, place: type});

        for(let i = 0; i < length; i++) {
            if(list[i].id == file.id) {
                list.splice(i, 1);

                break;
            }
        }

        switch(type) {
            case 'Private': {
                setPrivateFiles(list)
                break;
            }
            case 'Public': {
                setPublicFiles(list)
                break;
            }
        }

        setListFiles(newListFile);
        setValue('files', newListFile);
    }

    const onSubmit = async (data, event) => {
        try {
            let original = [...files];
            let toUpload = [...listFiles];

            if(original.length == toUpload.length) {
                for(let i = 0; i < original.length; i++) {
                    let deleted = false;

                    for(let j = 0; j < toUpload.length; j++) {
                        if(original[i].name == toUpload[j].name &&
                           original[i].size == toUpload[j].size &&
                           original[i].type == toUpload[j].type
                        ) {
                            original.splice(i, 1);
                            toUpload.splice(j, 1);

                            deleted = true;
                            break;
                        }
                    }

                    if(deleted)
                        i = -1;
                    else
                        break;
                }

                if(!original.length && !toUpload.length) {
                    setInfo(true);
                    setStatusMessage('No ha habido cambios en los datos.')
                    handleOpen();

                    throw {message: 'No hay cambios'};
                }
            }

            setMessage('¿Estas seguro de subir estos archivos.?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }
            
            const data = {
                update_files: reuseFiles.current, 
                original_files: files
            }

            if(uploadFiles.current) {
                const formData = new FormData();

                await uploadFiles.current.forEach((file) => {
                    if(file instanceof File) {
                        formData.append('files', file);
                    }
                });
                
                const response = await setterFiles({
                    id: table_id, 
                    others: id, 
                    data: formData,
                    url: 'files/tables/preparation'
                })
    
                if(response.status == 201) {
                    
                }
                else
                    throw {message: 'Hubo un error en la petición', status: response.status}
            }
            
        }
        catch(err) {
            if(!err.canceled) {
                setStatus(err.status);
                setMessage(err.message);
                handleOpen();
            }
        }
    }

    return (
        <form style={modal.content}>
            <Divider />
            <Controller 
                control={control}
                render={({ field }) =>
                    <FormControl
                        sx={{ width: 500 }}
                    >
                        <FileUploader 
                            label='Sube o arrastra tus archivos aqui.'
                            name="file"
                            multiple={true}
                            types={fileTypes}
                            hoverTitle='Soltar aqui.'
                            handleChange={(file) => handleAddFile(file, field)}
                        />
                        <FormHelperText>
                            Ingrese los archivos que requiera para la mesa.
                        </FormHelperText>
                    </FormControl>
                }
                {...register("files")}
            />
            {publicFiles.length > 0 && 
                <>
                    <FormControl
                        sx={{width: '500px'}}
                    >
                        <List>
                            <ListItem
                                secondaryAction={
                                    <IconButton
                                        onClick={() => {setOpenPublic(!openPublic)}}
                                    >
                                        {openPublic ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                    </IconButton>
                                }
                            >
                                <Typography variant="h6">
                                    Archivos publicos.
                                </Typography>
                                <Divider variant="middle"/>
                            </ListItem>
                            <Collapse
                                in={openPublic}
                                timeout="auto"
                                unmountOnExit
                            >
                                {publicFiles.map((file) => (
                                    <Box
                                        key={file.id}
                                    >
                                        <Divider variant="middle"/>
                                        <ListItem
                                            secondaryAction={
                                                <IconButton
                                                    onClick={() => {handleSelectCloudFiles(file, publicFiles, 'Public')}}
                                                >
                                                    <AddCircleIcon />
                                                </IconButton>
                                            }
                                        >
                                            <ListItemText 
                                                primary={`Nombre de archivo: ${file.name}`}
                                                secondary={`Tipo de archivo: ${file.type}`}
                                            />
                                        </ListItem>
                                    </Box>
                                ))}
                            </Collapse>
                        </List>
                        <FormHelperText>
                            Estos son archivos privados, que tu guardaste como acceso rapido.
                        </FormHelperText>
                    </FormControl>
                    <Divider />
                </>
            }
            {privateFiles.length > 0 && 
                <>
                    <FormControl
                        sx={{width: '500px'}}
                    >
                        <List>
                            <ListItem
                                secondaryAction={
                                    <IconButton
                                        onClick={() => {setOpenPrivate(!openPrivate)}}
                                    >
                                        {openPublic ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                    </IconButton>
                                }
                            >
                                <Typography variant="h6">
                                    Archivos privados.
                                </Typography>
                                <Divider variant="middle"/>
                            </ListItem>
                            <Collapse
                                in={openPrivate}
                                timeout="auto"
                                unmountOnExit
                            >
                                {privateFiles.map((file) => (
                                    <Box
                                        key={file.id}
                                    >
                                        <Divider variant="middle"/>
                                        <ListItem
                                            secondaryAction={
                                                <IconButton
                                                    onClick={() => {handleSelectCloudFiles(file, privateFiles, 'Private')}}
                                                >
                                                    <AddCircleIcon />
                                                </IconButton>
                                            }
                                        >
                                            <ListItemText 
                                                primary={`Nombre de archivo: ${file.name}`}
                                                secondary={`Tipo de archivo: ${file.type}`}
                                            />
                                        </ListItem>
                                    </Box>
                                ))}
                            </Collapse>
                        </List>
                        <FormHelperText>
                            Estos son archivos publicos que puedes utilizar en caso de que los requieras.
                        </FormHelperText>
                    </FormControl>
                    <Divider />
                </>
            }
            {listFiles.length > 0 ?
                <List
                    sx={{width: '500px'}}
                >
                    <ListItem>
                        <Typography variant="h6">
                            Archivos seleccionados.
                        </Typography>
                        <Divider variant="middle"/>
                    </ListItem>
                    {listFiles.map((file, index) => (
                        <Box
                            key={index}
                        >
                            <Divider variant="middle"/>
                            <ListItem
                                secondaryAction={
                                    <IconButton
                                        color="error"
                                        size="large" 
                                        edge="end" 
                                        onClick={()=> handleDeleteFile(index)}
                                    >
                                        <DeleteIcon />
                                    </IconButton>
                                }
                            >
                                <ListItemText 
                                    primary={`Nombre de archivo: ${file.name}`}
                                    secondary={`Tipo de archivo: ${file.type}`}
                                />
                            </ListItem>
                        </Box>
                    ))}
                </List> :
                <Typography variant="h5" sx={{...global.warn, width: '468px'}}>
                    No hay archivos asignados a la mesa.
                </Typography>
            }
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
    );
}
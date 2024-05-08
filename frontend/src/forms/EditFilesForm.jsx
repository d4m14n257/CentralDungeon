import { useCallback, useEffect, useContext, useState, useRef } from "react";
import { Divider, FormControl, FormHelperText, List, ListItem, ListItemText, Stack } from "@mui/material";
import { useForm, Controller } from "react-hook-form";
import { LoadingButton } from "@mui/lab";
import { FileUploader } from "react-drag-drop-files";
import { zodResolver } from "@hookform/resolvers/zod";

import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";

const schema = z.object ({
    files: z.object({
        name: z.string(),
        type: z.string()
    }).array()
}) 

export default function EditFilesForm (props) {
    const { table_id, files, reloadAction, handleCloseModal } = props;
    const { confirm, setMessage } = useContext(Confirm);
    const { handleOpen, setMessage : setStatusMessage, setStatus } = useContext(Message);
    const [listFiles, setListFiles] = useState([]);
    const uploadFiles = useRef(null);

    const fileTypes = ['png', 'jpg', 'jpeg', 'pdf', 'docx', 'doc']

    const { control, register, handleSubmit, formState: { isSubmitting }} = useForm({
        defaultValues: {
            files: []
        },
        resolver: zodResolver(schema)
    })
    
    useEffect(() => {
        setMessage('¿Estas seguro de subir estos archivos.?');
    }, []);

    const handleAddFile = (file, field) => {
        const newListFile = [...listFiles];
        const length = file.length;

        console.log('entrada: ', Boolean(uploadFiles.current))
        uploadFiles.current = uploadFiles.current ? {...file, ...uploadFiles.current} : {...file};

        for(let i = 0; i < length; i++) {
            const add = {
                name: file[i].name,
                type: file[i].type,
                size: file[i].size
            };

            newListFile.push(add);
        }

        setListFiles(newListFile);
        field.onChange(newListFile);
        console.log('salida: ', uploadFiles.current)
    };

    const onSubmit = useCallback(async (data, event) => {
        try {

        }
        catch(err) {
            console.log(err)
        }
    }, []);

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
                            handleChange={(file) => handleAddFile(file, field)}
                        />
                        <FormHelperText>
                            Ingrese los archivos que requiera para la mesa.
                        </FormHelperText>
                    </FormControl>
                }
                {...register("files")}
            />
            <List>
                {listFiles.map((file) => (
                    <ListItem>
                        <ListItemText 
                            primary={`Nombre de archivo: ${file.name}`}
                            secondary={`Tipo de archivo: ${file.type}`}
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
    );
}
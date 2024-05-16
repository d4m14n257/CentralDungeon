import { useCallback, useContext } from "react";
import { Avatar, Box, Divider, IconButton, ListItem, ListItemIcon, ListItemText } from "@mui/material";
import { HandlerMessage, Message } from "../info/HandlerMessage";

import ImageIcon from '@mui/icons-material/Image';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import DeleteIcon from '@mui/icons-material/Delete';

import { Message as MessageContext } from "@/contexts/MessageContext";
import { Confirm } from "@/contexts/ConfirmContext";
import { deleter } from "@/api/deleter";

export default function ListFileTable (props) {
    const { table_id, files, reloadAction } = props;
    const { confirm, setMessage : deleteMessage } = useContext(Confirm);
    const { setStatus, setMessage, handleOpen } = useContext(MessageContext);

    const handleDeleteFile = useCallback(async (event, file_id) => {
        try {
            deleteMessage('¿Desea eliminar este archivo de la mesa?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }

            const response = await deleter({id: table_id, others: file_id, url: 'files/tables/preparation'});

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setMessage('Archivo de la mesa borrado.');
                handleOpen();

                await reloadAction();
            }
            else
                throw {message: 'Ha habido un error al momento de eliminar el archivo de la mesa.', status: response.status}
        }
        catch (err) {
            if(!err.canceled) {
                setStatus(err.status);
                setMessage(err.message);
                handleOpen();
            }
        }
    }, []);

    return (
        <Message>
            <Message.When hasData={files.length > 0}>
                {files.map((file) => (
                    <Box
                        sx={{ paddingX: 1 }}
                        key={file.id}
                    >
                        <Divider variant="middle" component="li"/>
                        <ListItem
                            secondaryAction={
                                <IconButton onClick={(event) => {handleDeleteFile(event, file.id)}}>
                                    <DeleteIcon color='error'/>
                                </IconButton>
                            }
                        >
                            <ListItemIcon>
                                <Avatar>
                                    {file.type == 'application/pdf' ? 
                                        <PictureAsPdfIcon /> :
                                    file.type == 'image/png' ||
                                    file.type == 'image/jpg' ||
                                    file.type == 'image/jpeg' ? 
                                        <ImageIcon /> :
                                        <InsertDriveFileIcon />
                                    }
                                </Avatar>
                            </ListItemIcon>
                            <ListItemText
                                primary={file.name}
                            />
                        </ListItem>
                    </Box>
                ))}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message='No se han agregado archivos'/>
                </ListItem>
            </Message.Else>
        </Message>
    );
}
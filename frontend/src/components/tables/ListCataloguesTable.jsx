import { useCallback, useContext, useEffect } from "react";
import { Box, Divider, IconButton, ListItem, ListItemText } from "@mui/material";

import DeleteIcon from '@mui/icons-material/Delete';

import { HandlerMessage, Message } from "../info/HandlerMessage";
import { Confirm } from "@/contexts/ConfirmContext";
import { deleter } from "@/api/deleter";
import { Message as MessageContext } from "@/contexts/MessageContext";
import Span from "../Span";

export default function ListCataloguesTable (props) {
    const { table_id, name, catalogue, reloadAction } = props;
    const { confirm, setMessage : deleteMessage } = useContext(Confirm);
    const { setStatus, setMessage, handleOpen } = useContext(MessageContext);

    useEffect(() => {
        let message;

        switch (name) {
            case 'tag': {
                message = '¿Seguro desea eliminar este tag de la mesa?'
                break;
            }
            case 'sistema': {
                message = '¿Seguro desea eliminar este sistema de la mesa?'
                break;
            }
            case 'plataforma': {
                message = '¿Seguro desea eliminar esta plataforma de la mesa?'
                break;
            }
        }

        deleteMessage(message);
    }, [])

    const handleDeleteItem = useCallback(async (event, id) => {
        try {
            let url;
            let successfully;
            let error;

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }

            switch (name) {
                case 'tag': {
                    url = 'tags'
                    successfully = 'Tag eliminado de la mesa con exito.'
                    error = 'Ha habido un error al momento de eliminar el tag de la mesa.'
                    break;
                }
                case 'sistema': {
                    url = 'systems'
                    successfully = 'Sistema eliminado de la mesa con exito.'
                    error = 'Ha habido un error al momento de eliminar el sistema de la mesa.'
                    break;
                }
                case 'plataforma': {
                    url = 'platforms'
                    successfully = 'Plataforma eliminada de la mesa con exito.'
                    error = 'Ha habido un error al momento de eliminar la plataforma de la mesa.'
                    break;
                }
            }

            url += '/tables'

            const response = await deleter({id: id, others: table_id, url: url});

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setMessage(successfully);
                handleOpen();

                await reloadAction();
            }
            else {
                throw {message: error, status: response.status}
            }
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
        <Message>
            <Message.When hasData={catalogue.length > 0}>
                {catalogue.map((item) => (
                    <Box 
                        key={item.id}
                        sx={{ paddingX: 1 }}
                    >
                        <Divider variant="middle" component="li"/>
                        <ListItem
                            secondaryAction={
                                <IconButton onClick={(event) => {handleDeleteItem(event, item.id)}}>
                                    <DeleteIcon color='error'/>
                                </IconButton>
                            }
                        >
                            <ListItemText 
                                primary={item.name}
                                secondary={
                                    <>
                                        <Span title='Estado: '/>{item.status}
                                    </>
                                }
                            />
                        </ListItem>
                    </Box>
                ))}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message={`No se ha asignado ${name == 'plataforma' ? `ninguna ${name}` : `ningun ${name}`}`} />
                </ListItem>
            </Message.Else>
        </Message>
    );
}
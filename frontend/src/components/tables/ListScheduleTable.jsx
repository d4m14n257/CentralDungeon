import { useContext, useEffect, useCallback } from "react";
import { Box, Divider, IconButton, ListItem, ListItemText } from "@mui/material";

import DeleteIcon from '@mui/icons-material/Delete';

import { HandlerMessage, Message } from "../info/HandlerMessage";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message as MessageContext } from "@/contexts/MessageContext";
import { deleter } from "@/api/deleter";
import Span from "../Span";

export default function ListScheduleTable (props) {
    const { table_id, schedule, reloadAction, utc } = props;
    const { confirm, setMessage : deleteMessage } = useContext(Confirm);
    const { setStatus, setMessage, handleOpen } = useContext(MessageContext);

    useEffect(() => {
        deleteMessage('¿Desea eliminar este dia y hora?')
    }, [])

    const handleDeleteItem = useCallback(async (event, weekday, hourtime) => {
        try {
            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }

            const body = {
                weekday: weekday, 
                hourtime: hourtime
            }

            const response = await deleter({id: table_id, body: body, url: 'tables/schedule'});

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setMessage('Horario de la mesa corregido.');
                handleOpen();

                await reloadAction();
            }
            else
                throw {message: 'Ha habido un error al momento de cambiar el horario de la mesa.', status: response.status}
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
            <Message.When hasData={schedule.length > 0}>
                {schedule.map((day) => 
                    <Box
                        sx={{ paddingX: 1 }}
                        key={day.weekday + day.hourtime}
                    >
                        <Divider variant="middle" component="li"/>
                        <ListItem
                            secondaryAction={
                                <IconButton onClick={(event) => {handleDeleteItem(event, day.weekday, day.hourtime)}}>
                                    <DeleteIcon color='error'/>
                                </IconButton>
                            }
                        >
                            <ListItemText 
                                primary={day.weekday}
                                secondary={
                                    <>
                                        <Span title='Hora: ' />{day.hourtime} {utc && `UTC${utc}`}
                                    </>
                                }
                            />
                        </ListItem>
                    </Box>
                )}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message='No se ha establedio un horario' />
                </ListItem>
            </Message.Else>
        </Message>
    );
}
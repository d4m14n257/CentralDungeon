import { useRouter } from "next/router";
import { IconButton, Stack, Tooltip } from "@mui/material";

import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';

import { useCallback, useContext, useEffect } from "react";
import { Confirm } from "@/contexts/ConfirmContext";
import { TABLES } from "@/constants/constants";
import { deleter } from "@/api/deleter";
import { Message } from "@/contexts/MessageContext";

export default function ActionButtonTable (props) {
    const { id, reloadAction } = props;
    const { confirm, setMessage : deleteMessage } = useContext(Confirm)
    const { setStatus, setMessage, handleOpen } = useContext(Message)
    const router = useRouter();

    useEffect(() => {
        deleteMessage('¿Seguro desea eliminar esta mesa?');
    }, [])

    const handleTableRoute = () => {
        router.push(`${TABLES}/${id}`)
    }

    const handleDeleteTable = useCallback(async (event) => {
        try {
            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {err: 'Canceled'}})
            }

            const response = await deleter({id: id, url: 'tables'});

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setMessage('Mesa eliminada con exito.');
                handleOpen();

                await reloadAction();
            }
            else {
                setStatus(response.status);
                setMessage('Ha habido un error al momento de eliminar la mesa.');
                handleOpen();
            }

        }
        catch (err) {
            console.log(err)
        }
    }, []);

    return (
        <Stack flexDirection='row' justifyContent='center'>
            <Tooltip
                title='Ver mesa'
            >
                <IconButton size='small' onClick={() => {handleTableRoute()}}>
                    <VisibilityIcon fontSize="inherit"/>
                </IconButton>
            </Tooltip>
            <Tooltip
                title='Eliminar mesa'
            >
                <IconButton size='small' onClick={(event) => {handleDeleteTable(event)}}>
                    <DeleteIcon fontSize="inherit" color='error'/>
                </IconButton>
            </Tooltip>
        </Stack>
    )
}
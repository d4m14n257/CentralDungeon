import { useRouter } from "next/router";
import { IconButton, Stack, Tooltip } from "@mui/material";

import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';

import { useContext } from "react";
import { Confirm } from "@/contexts/ConfirmContext";
import { TABLES } from "@/constants/constants";

export default function ActionButtonTable (props) {
    const { confirm, setMessage } = useContext(Confirm)
    const { id } = props;
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push(`${TABLES}/${id}`)
    }

    const handleDeleteTable = async (event, id) => {
        try {
            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {err: 'Canceled'}})
            }
        }
        catch (err) {
            console.log(err)
        }
    }

    return (
        <Stack flexDirection='row' justifyContent='center'>
            <Tooltip
                title='Ver mesa'
            >
                <IconButton size='small' onClick={() => {handleTableRoute(id)}}>
                    <VisibilityIcon fontSize="inherit"/>
                </IconButton>
            </Tooltip>
            <Tooltip
                title='Eliminar mesa'
            >
                <IconButton size='small' onClick={(event) => {handleDeleteTable(event, id)}}>
                    <DeleteIcon fontSize="inherit" color='error'/>
                </IconButton>
            </Tooltip>
        </Stack>
    )
}
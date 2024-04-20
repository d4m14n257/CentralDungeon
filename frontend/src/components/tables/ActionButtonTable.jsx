import { useRouter } from "next/router";
import { IconButton, Stack, Tooltip } from "@mui/material";

import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';

export default function ActionButtonTable (props) {
    const { id } = props;
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push(`/tables/${id}`)
    }

    const handleDeleteTable = (id) => {
        console.log(id)
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
                <IconButton size='small' onClick={() => {handleDeleteTable(id)}}>
                    <DeleteIcon fontSize="inherit" color='error'/>
                </IconButton>
            </Tooltip>
        </Stack>
    )
}
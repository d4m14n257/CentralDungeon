import { IconButton, Tooltip } from "@mui/material";

import EditIcon from '@mui/icons-material/Edit';

export default function ActionEditButton (props) {
    const { data, handleOpenModal } = props;

    return (
        <Tooltip
            title='Editar datos generales'
        >
            <IconButton
                size='small'
                onClick={() => {handleOpenModal(data)}}
            >
                <EditIcon />
            </IconButton>
        </Tooltip>
    );
}
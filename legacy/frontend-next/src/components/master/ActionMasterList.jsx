import { IconButton, Tooltip } from "@mui/material";

import AddCircleIcon from '@mui/icons-material/AddCircle'

export default function ActionMasterList (props) {
    const { handleAction } = props;

    return (
        <Tooltip
            title="Crear Mesa"
        >
            <IconButton size="large" edge="end" aria-label="delete" onClick={() => {handleAction()}}>
                <AddCircleIcon />
            </IconButton>
        </Tooltip>
    );
}
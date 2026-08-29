import { IconButton, Tooltip } from "@mui/material";

export default function ActionButtonDefault(props) {
    const { title, data, handleAction, IconAction } = props
    
    return (
        <Tooltip
            title={title}
        >
            <IconButton
                size='large'
                onClick={() => {handleAction(data)}}
            >
                <IconAction />
            </IconButton>
        </Tooltip>
    );
}
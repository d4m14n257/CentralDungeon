import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';

export default function MenuItemComponent (props) {
    const { name, Icon, handleOnClick } = props;

    return (
        <MenuItem
            onClick={() => {}}
        >
            <ListItemIcon
                key={name}
            >
                <Icon fontSize="small" />
            </ListItemIcon>
                {name}
        </MenuItem>
    );
}
import MenuItem from '@mui/material/MenuItem';

import ListItemIcon from '@mui/material/ListItemIcon';
import CasinoIcon from '@mui/icons-material/Casino';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import GamepadIcon from '@mui/icons-material/Gamepad';

export default function MenuItemComponent (props) {
    const { id, name, Icon, handleClickMenu } = props;

    return (
        <MenuItem
            onClick={() => {id ? handleClickMenu(id) : handleClickMenu()}}
        >
            <ListItemIcon
                key={name}
            >
                {Icon ?
                    <Icon fontSize="small" /> :
                    name == 'Jugador' ? <GamepadIcon /> :
                    name == 'Master' ? <CasinoIcon />:
                    name == 'Admin' ? <ManageAccountsIcon /> :
                    <></>
                }
            </ListItemIcon>
            {name}
        </MenuItem>
    );
}
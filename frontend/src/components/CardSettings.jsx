import { useRef, useContext, useEffect, useState } from 'react';

import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';
import Menu from '@mui/material/Menu';

import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';

import MenuItemComponent from './MenuItemComponent';

import { useTheme } from '@mui/material/styles';

import { ColorModeContext } from '@/context/ColorModeContext';
import { UserContext } from '@/context/UserContext';

import { global } from '@/styles/global';

export default function CardSettings (props) {
    const { openUser, handleOpenUser } = props;
    const [ openMenu, setOpenMenu ] = useState(false);
    const theme = useTheme();
    
    const { username, rol, roles, handleUpdateRol } = useContext(UserContext);
    const { colorMode } = useContext(ColorModeContext);
    const userSettingsRef = useRef(null);
    const open = Boolean(openMenu);

    // const handleClickOutside = (event) => {
    //     if(userSettingsRef.current && !userSettingsRef.current.contains(event.target)) {
    //         handleOpenUser();
    //     }
    // }

    // useEffect(() => {
    //     window.addEventListener('click', handleClickOutside);

    //     return () => {
    //         window.removeEventListener('click', handleClickOutside);
    //     };
    // }, [openUser])

    const handleOpenMenu = (event) => {
        setOpenMenu(event.currentTarget)
    }

    const handleCloseMenu = () => {
        setOpenMenu(null);
    };

    const handleChangeRol = (rol) => {
        handleUpdateRol(rol)
    }

    return (
        <Card ref={userSettingsRef} sx={{display: openUser ? 'block' : 'none', ...global.cardProfile}}>
            <CardContent sx={global.cardHeaderSelect}>
                <Box sx={global.profileRoles}>
                    <Box sx={global.profileData}>
                        <Avatar sx={global.profileImageSelect}>
                            T
                        </Avatar>
                        <Box>
                            <Typography variant="h4">{username}</Typography>
                            {roles.length > 1 ? 
                                <Button endIcon={<ManageAccountsIcon />} onClick={handleOpenMenu}>
                                    <Typography variant="subtitle1">{rol}</Typography>
                                </Button> :
                                <Typography variant="h6">{rol}</Typography>
                            }
                        </Box>
                    </Box>
                    <IconButton sx={{ ml: 1 }} onClick={colorMode.toggleColorMode}>
                        {theme.palette.mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon />}
                    </IconButton>
                </Box>
                <Button sx={global.profileSettings} startIcon={<SettingsIcon />}>
                    Ajustes de usuario
                </Button>
            </CardContent>
            <CardActions sx={global.profileLogout}>
                <Button sx={global.buttonLogout} startIcon={<LogoutIcon />}>
                    Cerrar sesión
                </Button>
            </CardActions>
            {roles.length > 1 &&
                <Menu 
                    anchorEl={openMenu}
                    id="account-menu"
                    open={open}
                    onClose={handleCloseMenu}
                    onClick={handleCloseMenu}
                    transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                    anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                >
                    {roles.map((item) => {
                        if(item != rol)
                            return (
                                <MenuItemComponent
                                    id={item}
                                    name={item}
                                    handleClickMenu={handleUpdateRol}
                                />
                            );
                    })}
                </Menu>
            }
        </Card>
    );
}
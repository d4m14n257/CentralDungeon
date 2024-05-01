import { useRef, useContext, useEffect, useState, useCallback } from 'react';

import { Box, Button, IconButton, Card, CardActions, CardContent, Avatar, Typography } from '@mui/material';

import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';

import { useTheme } from '@mui/material/styles';
import { ColorMode } from '@/contexts/ColorModeContext';
import { User } from '@/contexts/UserContext';

import { global } from '@/styles/global';

const useCardSettings = ({ openUser, handleOpenUser }) => {
    const theme = useTheme();
    const firstState = useRef(false);
    const userSettingsRef = useRef(null)
    const { username, rol } = useContext(User);
    const { colorMode } = useContext(ColorMode);

    const handleClickOutside = useCallback((event) => {
        if(firstState.current) {
            if(!userSettingsRef.current.contains(event.target))
                handleOpenUser();
        }
        else
            firstState.current = true;
    }, [])

    useEffect(() => {
        window.addEventListener('click', handleClickOutside);

        return () => {
            window.removeEventListener('click', handleClickOutside);
        };
    }, [openUser])

    return {
        username,
        rol,
        colorMode,
        theme,
        userSettingsRef
    };
}

//TODO: Check why in specific point inside the card, it always closed 

export default function CardSettings (props) {
    const { openUser, handleOpenUser } = props;
    const { username, rol, colorMode, theme, userSettingsRef } = useCardSettings({ openUser, handleOpenUser });

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
                            <Box
                                 sx={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    alignItems: 'center',
                                    gap: 1.3
                                }}
                            >
                                <Typography variant="h6">{rol}</Typography>
                                <ManageAccountsIcon fontSize='small'/>
                            </Box>
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
        </Card>
    );
}
import { useRef, useEffect } from 'react';

import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';

import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';

import { global } from '@/styles/global';

export default function CardSettings (props) {
    const { openUser, handleOpenUser } = props;
    const userSettingsRef = useRef(null);

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

    return (
        <Card ref={userSettingsRef} sx={{display: openUser ? 'block' : 'none', ...global.cardProfile}}>
            <CardContent sx={global.cardHeaderSelect}>
                <Box sx={global.profileData}>
                    <Avatar sx={global.profileImageSelect}>
                        T
                    </Avatar>
                    <Box>
                        <Typography variant="h4">Teshynil</Typography>
                        <Typography variant="h6">Admin</Typography>
                    </Box>
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
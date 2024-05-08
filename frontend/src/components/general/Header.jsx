import { useContext } from "react";
import { useRouter } from "next/router";

import { AppBar, Toolbar, Typography, Button, IconButton, Avatar, InputBase, Box, Stack } from "@mui/material";

import { styled, alpha } from '@mui/material/styles';

import CommentIcon from '@mui/icons-material/Comment';
import NotificationsIcon from '@mui/icons-material/Notifications';
import VideogameAssetIcon from '@mui/icons-material/VideogameAsset';
import SearchIcon from '@mui/icons-material/Search';
import CasinoIcon from '@mui/icons-material/Casino';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import TableRestaurantIcon from '@mui/icons-material/TableRestaurant';
import GradeIcon from '@mui/icons-material/Grade';

import { global } from "@/styles/global";
import { User } from "@/contexts/UserContext";
import { FIRST_CLASS_TABLES, HOME, MASTER, PUBLIC_TABLES } from "@/constants/constants";

/*
    TODO: Se integra un search con etiquetado, podria ser algo similar al de discord en los chats.
    Criterios publico: Tag (de mesa) / AND y OR, Por master (si esta disponible en la mesa/Nombre de discord on el sistema)
    Criterios privado: ID de usuario que es jugador dentro de mesa (si es admin siempre si, si la lista es privada mamo), regresar solo que esta en mesas privadas, mas no cual o indicios de cuales,
                       ID de usuario que es master de la mesa (Siempre y cuando sea publico) Solo regresar las publicas y hacer mencion de cuantas a sido privada.
                       Buscar mesas donde ID de jugar adquirio comentarios (Solo admin)
    Los criterios seran catalogaran para las chips y facilitar Querys.
    Dependiendo de donde se haga la busqueda, dependera como sera y quien. (mas chanva :c)
*/

const Search = styled('div')(({ theme }) => ({
    position: 'relative',
    borderRadius: theme.shape.borderRadius,
    backgroundColor: alpha(theme.palette.common.white, 0.15),
    '&:hover': {
        backgroundColor: alpha(theme.palette.common.white, 0.25),
    },
    marginLeft: 0,
    width: '100%',
    [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing(1),
        width: 'auto',
    },
}));

const SearchIconWrapper = styled('div')(({ theme }) => ({
    padding: theme.spacing(0, 2),
    height: '100%',
    position: 'absolute',
    pointerEvents: 'none',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
}));
  
const StyledInputBase = styled(InputBase)(({ theme }) => ({
    color: 'inherit',
    width: '100%',
    '& .MuiInputBase-input': {
        padding: theme.spacing(1, 1, 1, 0),
        paddingLeft: `calc(1em + ${theme.spacing(4)})`,
        transition: theme.transitions.create('width'),
        [theme.breakpoints.up('sm')]: {
            width: '12ch',
            '&:focus': {
                width: '20ch',
            },
        },
    },
}));

export default function Header(props) {
    const { username } = useContext(User);
    const { handleOpenUser } = props;
    const router = useRouter();

    const handleReturnHome = () => {
        router.push(HOME);
    };

    const handlePushMaster = () => {
        router.push(MASTER);
    };

    const handleFirstClass = () => {
        router.push(FIRST_CLASS_TABLES);
    };

    const handlePublicClass = () => {
        router.push(PUBLIC_TABLES);
    };

    return (
        <AppBar position="fixed" color="primary">
            <Toolbar sx={global.header}>
                <Button sx={{color: "white"}} startIcon={<VideogameAssetIcon size="large"/>} onClick={handleReturnHome}>
                    <Typography variant="h6">Central Dungeon</Typography>
                </Button>
                <Stack direction='row' spacing={5} display='contents'>
                    <Stack direction='column' alignItems='center'>
                        <IconButton sx={global.navHeader.icon}><ManageAccountsIcon /></IconButton>
                        <Typography sx={global.navHeader.textIcon} variant="caption">Admin</Typography>
                    </Stack>
                    <Stack direction='column' alignItems='center'>
                        <IconButton onClick={handlePushMaster} sx={global.navHeader.icon}><CasinoIcon /></IconButton>
                        <Typography sx={global.navHeader.textIcon} variant="caption">Master</Typography>
                    </Stack>
                    <Stack direction='column' alignItems='center'>
                        <IconButton onClick={handleFirstClass} sx={global.navHeader.icon}><TableRestaurantIcon /></IconButton>
                        <Typography sx={global.navHeader.textIcon} variant="caption">Primera clase</Typography>
                    </Stack>
                    <Stack direction='column' alignItems='center'>
                        <IconButton onClick={handlePublicClass} sx={global.navHeader.icon}><GradeIcon /></IconButton>
                        <Typography sx={global.navHeader.textIcon} variant="caption">Publico</Typography>
                    </Stack>
                </Stack>
                <Box sx={global.iconHeader}>
                    <Search>
                        <SearchIconWrapper>
                            <SearchIcon />
                        </SearchIconWrapper>
                        <StyledInputBase
                            placeholder="Buscador..."
                            inputProps={{ 'aria-label': 'search' }}
                        />
                    </Search>
                    <Stack direction='row'>
                        <IconButton
                            sx={{ color: 'white' }}
                        ><CommentIcon /></IconButton>
                        <IconButton
                            sx={{ color: 'white' }}
                        ><NotificationsIcon /></IconButton>
                    </Stack>
                    <IconButton onClick={handleOpenUser}>
                        <Avatar>
                            {username.charAt(0)}
                        </Avatar>
                    </IconButton>
                </Box>
            </Toolbar>
        </AppBar>
    );
}

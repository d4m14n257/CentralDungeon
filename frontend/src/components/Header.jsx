import { useContext } from "react";
import { useRouter } from "next/router";

import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
import IconButton from '@mui/material/IconButton';
import Avatar from '@mui/material/Avatar';
import InputBase from '@mui/material/InputBase';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';

import { styled, alpha } from '@mui/material/styles';

import CommentIcon from '@mui/icons-material/Comment';
import NotificationsIcon from '@mui/icons-material/Notifications';
import VideogameAssetIcon from '@mui/icons-material/VideogameAsset';
import SearchIcon from '@mui/icons-material/Search';

import { global } from "@/styles/global";
import { UserContext } from "@/context/UserContext";

/*
    Se integra un search con etiquetado, podria ser algo similar al de discord en los chats.
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
    const { username } = useContext(UserContext);
    const { handleOpenUser } = props
    const router = useRouter();

    const handleReturnHome = () => {
        router.push(`/`)
    }

    return (
        <AppBar position="fixed" color="primary">
            <Toolbar sx={global.header}>
                <Button sx={{color: "white"}} startIcon={<VideogameAssetIcon size="large"/>} onClick={handleReturnHome}>
                    <Typography variant="h6">Central Dungeon</Typography>
                </Button>
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
                        <IconButton><CommentIcon /></IconButton>
                        <IconButton><NotificationsIcon /></IconButton>
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

import { useRouter } from "next/router";

import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
import IconButton from '@mui/material/IconButton';
import Avatar from '@mui/material/Avatar';

import VideogameAssetIcon from '@mui/icons-material/VideogameAsset';
import { global } from "@/styles/global";

/*
    Se integra un search con etiquetado, podria ser algo similar al de discord en los chats.
    Criterios publico: Tag (de mesa) / AND y OR, Por master (si esta disponible en la mesa/Nombre de discord on el sistema)
    Criterios privado: ID de usuario que es jugador dentro de mesa (si es admin siempre si, si la lista es privada mamo), regresar solo que esta en mesas privadas, mas no cual o indicios de cuales,
                       ID de usuario que es master de la mesa (Siempre y cuando sea publico) Solo regresar las publicas y hacer mencion de cuantas a sido privada.
                       Buscar mesas donde ID de jugar adquirio comentarios (Solo admin)
    Los criterios seran catalogaran para las chips y facilitar Querys.
    Dependiendo de donde se haga la busqueda, dependera como sera y quien. (mas chanva :c)
*/

// const menu = [
//     {
//         name: 'Ajustes',
//         Icon: 
//     },
//     {
//         name: 'Cerra sesión',
//         Icon: 
//     },
// ]

export default function Header(props) {
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
                <IconButton onClick={handleOpenUser}>
                    <Avatar>
                        T
                    </Avatar>
                </IconButton>
            </Toolbar>
        </AppBar>
    );
}

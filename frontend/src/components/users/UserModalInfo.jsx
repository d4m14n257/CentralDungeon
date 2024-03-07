import { useEffect, useRef, useState } from 'react';

import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid'
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Menu from '@mui/material/Menu';

import CommentIcon from '@mui/icons-material/Comment';
import InfoIcon from '@mui/icons-material/Info';
import BlockIcon from '@mui/icons-material/Block';
import PreviewIcon from '@mui/icons-material/Preview';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';
import EditIcon from '@mui/icons-material/Edit';

import ModalBase from "../ModalBase";
import NestedCommentModal from './NestedCommentModal';
import MenuItemComponent from '../MenuItemComponent';
import { styles } from '@/styles/users/modal';
import { getFlagCountry } from '@/api/getFlagCountry';

/*
    El modal del usuario tendras algunas restricciones dependiendo a quien lo vea
    Usuario a usuario - Vista general
    Master a usuario - Vista general, Comentarios
    Admin a usuario - Toda wey xd
*/

const menu = [
    {
        name: 'Banear',
        Icon: BlockIcon
    },
    {
        name: 'Ver como',
        Icon: PreviewIcon
    },
    {
        name: 'No me acuerdo xd',
        Icon: QuestionMarkIcon
    },
    {
        name: 'Editar',
        Icon:  EditIcon
    },
];

export default function UserModalInfo (props) {
    const {isOpen, handleCloseModal, user} = props;
    const [flag, setFlag] = useState();
    const [openMenu, setOpenMenu] = useState(null);
    const [openNestedModal, setOpenNestModal] = useState(false);
    const userId = useRef(0);

    const open = Boolean(openMenu);

    useEffect(() => {
        if(user) {
            const get = async () => {
                const response = await getFlagCountry(user.country);
                setFlag(response);
            }

            get();
        }
    }, [user])

    const handleOpenMenu = (event) => {
        setOpenMenu(event.currentTarget)
    }

    const handleCloseMenu = () => {
        setOpenMenu(null);
    };

    const handleOpenNestedModal = (id) => {
        userId.current = id;
        setOpenNestModal(true);
    }

    const handleCloseNestedModal = () => {
        setOpenNestModal(false);
    }

    return (
        <>
            <ModalBase
                isOpen={isOpen}
                handleCloseModal={handleCloseModal}
            >
                <Box sx={styles.body}>
                    <Box sx={styles.header}>
                        <Box sx={styles.avatarContent}>
                            <Avatar sx={styles.avatar}>
                                T
                            </Avatar>
                            <Stack>
                                <Typography variant="h5" color="initial">{user.name}</Typography>
                                <Typography variant="h6" color="initial">{user.discord}</Typography>
                            </Stack>
                        </Box>
                        <Box sx={styles.flagContent}>
                            <Box sx={styles.flag}>
                                <Box 
                                    component="img"
                                    src={flag}
                                    sx={styles.img}
                                />
                                <Typography variant='h6' color='initial'>({user.timezone})</Typography>
                            </Box>
                            <Box>
                                <IconButton aria-label="comments" onClick={() => handleOpenNestedModal(user.id)}>
                                    <CommentIcon />
                                </IconButton>
                                <IconButton
                                    onClick={handleOpenMenu}
                                    sx={{ ml: 2 }}
                                    aria-controls={open ? 'account-menu' : undefined}
                                    aria-haspopup="true"
                                    aria-expanded={open ? 'true' : undefined}
                                >
                                    <InfoIcon />
                                </IconButton>
                            </Box>
                        </Box>
                    </Box>
                    <Box sx={styles.content}>
                        <Typography variant="h5">Estadisticas</Typography>
                        <Grid
                            container
                            spacing={3}
                            direction="row"
                            justify="flex-start"
                            alignItems="flex-start"
                            alignContent="stretch"
                            wrap="nowrap"
                        >
                            <Grid item xs={4}>
                                <Stack spacing={1}>
                                    <Typography variant='h6'>Mesas jugando</Typography>
                                    <Chip label={user.joined_table.active_table} variant="outlined" onClick={() => {console.log("xd")}}/>
                                </Stack>
                            </Grid>
                            <Grid item xs={4}>
                                <Stack spacing={1}>
                                    <Typography variant='h6'>Mesas inscritas</Typography>
                                    <Chip label={user.joined_table.inscrited_table} variant="outlined" />
                                </Stack>
                            </Grid>
                            <Grid item xs={4}>
                                <Stack spacing={1}>
                                    <Typography variant='h6'>Mesas candidato</Typography>
                                    <Chip label={user.joined_table.candidate_table} variant="outlined" />
                                </Stack>
                            </Grid>
                        </Grid>
                    </Box>
                </Box>
            </ModalBase>
            <Menu
                anchorEl={openMenu}
                id="account-menu"
                open={open}
                onClose={handleCloseMenu}
                onClick={handleCloseMenu}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            >
                {menu.map((item) => (
                    <MenuItemComponent 
                        name={item.name}
                        Icon={item.Icon}
                    />
                ))}
            </Menu>
            {openNestedModal && 
                <NestedCommentModal 
                    handleCloseModal={handleCloseNestedModal}
                    isOpen={openNestedModal}
                    id={user}
                />
            }
        </>
    );
}
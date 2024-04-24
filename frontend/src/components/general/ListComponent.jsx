
import Grid from '@mui/material/Grid';
import { 
    Tooltip,
    Paper,
    List,
    ListItem,
    ListItemText,
    Typography,
    IconButton,
    Collapse,
    Stack,
    Divider, } 
from '@mui/material';

import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';

import { global } from '@/styles/global';
import { useState } from 'react';

import ViewMoreComponent from "../ViewMoreComponent";

const TitleComponent = ({title}) => {
    return (
        <Typography variant="h5">
            {title}
        </Typography>
    );
}

const useListComponent = () => {
    const [open, setOpen] = useState(true);

    const handleChange = () => {
        setOpen(!open)
    }

    return {
        open,
        handleChange
    }
}

export default function ListComponent (props) {
    const { id, lg, xs, title, description, action, tip, children, hasCollapse, handleExpand } = props;
    const { open, handleChange } = useListComponent();

    return (
        <Grid
            item
            lg={lg != undefined ? lg : 12}
            xs={xs != undefined ? xs : 12}
        >
            <Paper sx={global.border}>
                <List>
                    <ListItem
                        sx={{paddingX: 3}}
                        secondaryAction={
                            <Stack direction='row' spacing={0.5}>
                                {action &&
                                    <Tooltip
                                        title={tip}
                                    >
                                        <IconButton size="large" edge="end" aria-label="delete" onClick={action.handleClickButton}>
                                            <action.Icon />
                                        </IconButton>
                                    </Tooltip>
                                }
                                {hasCollapse &&
                                    <Tooltip
                                        title={open ? 'Cerrar' : 'Abrir'}
                                    >
                                        <IconButton size="large" edge="end" aria-label="delete" onClick={handleChange}>
                                            {open ? <ExpandLess /> : <ExpandMore />}
                                        </IconButton>
                                    </Tooltip>
                                }
                            </Stack>
                        }
                    >
                        <ListItemText
                            primary={<TitleComponent title={title} />}
                            secondary={<Typography variant='subtitule1'>{description}</Typography>}
                        />
                    </ListItem>
                    <Collapse
                        in={open}
                        timeout="auto"
                        unmountOnExit
                    >
                        {children}
                    </Collapse>
                        {handleExpand && 
                        <>
                            <Divider variant="middle" component="li" />
                            <ListItem>
                                <ListItemText primary={<ViewMoreComponent handleExpand={handleExpand} value={id}/>}/>
                            </ListItem>
                        </>
                    }
                </List>
            </Paper>
        </Grid>
    );
}
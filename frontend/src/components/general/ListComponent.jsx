
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
    const { id, lg, xs, title, description, children, hasCollapse, handleExpand, handleAction, Action, dataAction, titleAction, IconAction } = props;
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
                        sx={{
                            paddingX: 3
                        }}
                    >   
                        <Stack direction='row' sx={{width: '100%', justifyContent: 'space-between', alignItems: 'center'}}>
                            <ListItemText
                                primary={<TitleComponent title={title} />}
                                secondary={<Typography variant='subtitule1'>{description}</Typography>}
                            />
                            <Stack direction='row' spacing={0.5}>
                                    {Action &&
                                        <Action 
                                            handleAction={handleAction}
                                            data={dataAction}
                                            title={titleAction}
                                            IconAction={IconAction}
                                            sx={global.iconsButton}
                                        />
                                    }
                                    {hasCollapse &&
                                        <Tooltip
                                            title={open ? 'Cerrar' : 'Abrir'}
                                        >
                                            <IconButton sx={global.iconsButton} size="large" edge="end" aria-label="delete" onClick={handleChange}>
                                                {open ? <ExpandLess /> : <ExpandMore />}
                                            </IconButton>
                                        </Tooltip>
                                    }
                            </Stack>
                        </Stack>
                    </ListItem>
                    <Collapse
                        in={open}
                        timeout="auto"
                        unmountOnExit
                    >
                        {children}
                        {handleExpand && 
                        <>
                            <Divider variant="middle" component="li" />
                            <ListItem>
                                <ListItemText primary={<ViewMoreComponent handleExpand={handleExpand} value={id}/>}/>
                            </ListItem>
                        </>
                    }
                    </Collapse>
                </List>
            </Paper>
        </Grid>
    );
}
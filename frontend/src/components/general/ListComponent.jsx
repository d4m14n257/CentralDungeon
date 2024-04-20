
import Grid from '@mui/material/Grid';
import { 
    Tooltip,
    Paper,
    List,
    ListItem,
    ListItemText,
    Typography,
    IconButton, } 
from '@mui/material';


import { global } from '@/styles/global';

const TitleComponent = ({title}) => {
    return (
        <Typography variant="h5">
            {title}
        </Typography>
    );
}

export default function ListComponent (props) {
    const { lg, xs, title, description, action, tip, children } = props;

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
                        secondaryAction={action && 
                            <Tooltip
                                title={tip}
                            >
                                <IconButton size="large" edge="end" aria-label="delete" onClick={action.handleClickButton}>
                                    <action.Icon fontSize="inherit"/>
                                </IconButton>
                            </Tooltip>
                        }
                    >
                        <ListItemText
                            primary={<TitleComponent title={title} />}
                            secondary={<Typography variant='subtitule1'>{description}</Typography>}
                        />
                    </ListItem>
                    {children}
                </List>
            </Paper>
        </Grid>
    );
}
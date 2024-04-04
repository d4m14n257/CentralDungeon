import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';

import { global } from '@/styles/global';

const TitleComponent = ({title}) => {
    return (
        <Typography variant="h5">
            {title}
        </Typography>
    );
}

export default function ListComponent (props) {
    const { lg, xs, title, description, action, children } = props;

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
                            <IconButton size="large" edge="end" aria-label="delete" onClick={action.handleClickButton}>
                                <action.Icon fontSize="inherit"/>
                            </IconButton>
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
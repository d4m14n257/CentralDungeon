import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';

import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';

import { global } from '@/styles/global';

const TitleComponent = ({title}) => {
    return (
        <Typography variant="h5" color="initial">
            {title}
        </Typography>
    );
}

const ViewMoreComponent = (props) => {
    const { handleTableSelect, value } = props;

    return (
        <Typography 
            sx={{textAlign: 'center'}}
        >
            <Button startIcon={<KeyboardArrowDownIcon />} onClick={() => {handleTableSelect(value)}}>
                Ver mas
            </Button>
        </Typography>
    );
}

export default function ListComponent (props) {
    const { md, xs, tables, title, handleTableSelect } = props;

    console.log(tables)

    return (
        <Grid
            item
            md={md != undefined ? md : 12}
            xs={xs != undefined ? xs : 12}
        >
            <Paper sx={global.border}>
                <List>
                    <ListItem>
                        <ListItemText
                            primary={<TitleComponent title={title.name} />}
                        />
                    </ListItem>
                    {tables.map((table) => (
                        <Box key={table.name}>
                            <Divider variant="middle" component="li" />
                            <ListItem>
                                <ListItemButton>
                                    <ListItemText
                                        primary={<Typography variant="body1" color="initial">{table.name}</Typography>}
                                        secondary={<Typography variant="subtitle2" color="initial">{table.description}</Typography>}
                                    />
                                </ListItemButton>
                            </ListItem>
                        </Box>
                    ))}
                    <Divider variant="middle" component="li" />
                    {handleTableSelect &&
                        <ListItem>
                            <ListItemText primary={<ViewMoreComponent handleTableSelect={handleTableSelect} value={title}/>}/>
                        </ListItem>
                    }
                </List>
            </Paper>
        </Grid>
    );
}
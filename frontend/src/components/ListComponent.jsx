import { useRouter } from "next/router";

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
        <Typography variant="h5">
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
    const { md, xs, tables, info, isSelect, handleTableSelect } = props;
    const router = useRouter();

    const handleTableRoute = (id) => {
        if(info.id === 'joined-table')
        router.push(`/tables/${id}/joined`)
        else
            router.push(`/tables/${id}/opened`)
    }

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
                            primary={<TitleComponent title={info.name} />}
                        />
                    </ListItem>
                    {tables.map((table) => (
                        <Box key={table.name}>
                            <Divider variant="middle" component="li" />
                            <ListItem>
                                <ListItemButton onClick={() => handleTableRoute(table.id)}>
                                    <ListItemText
                                        primary={<Typography variant="body1">{table.name}</Typography>}
                                        secondary={<Typography variant="subtitle2">{table.description}</Typography>}
                                    />
                                </ListItemButton>
                            </ListItem>
                        </Box>
                    ))}
                    {isSelect &&
                        <>
                            <Divider variant="middle" component="li" />
                            <ListItem>
                                <ListItemText primary={<ViewMoreComponent handleTableSelect={handleTableSelect} value={info}/>}/>
                            </ListItem>
                        </>
                    }
                </List>
            </Paper>
        </Grid>
    );
}
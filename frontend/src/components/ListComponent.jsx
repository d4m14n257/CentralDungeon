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

import Span from "./Span";

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
    const { lg, xs, tables, info, isSelect, handleTableSelect } = props;
    const router = useRouter();

    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    const handleTableRoute = (id) => {
        if(info.id === 'joined-table')
            router.push(`/tables/${id}/joined`)
        else
            router.push(`/tables/${id}/opened`)
    }

    return (
        <Grid
            item
            lg={lg != undefined ? lg : 12}
            xs={xs != undefined ? xs : 12}
        >
            <Paper sx={global.border}>
                <List>
                    <ListItem>
                        <ListItemText
                            primary={<TitleComponent title={info.name} />}
                        />
                    </ListItem>
                    {tables.map((table) => {
                        const startDate = new Date(table.startdate);
                        const date = startDate.toLocaleDateString('es-ES', options);

                        return (
                            <Box key={table.name}>
                                <Divider variant="middle" component="li" />
                                <ListItem>
                                    <ListItemButton onClick={() => handleTableRoute(table.id)}>
                                        <Box sx={global.listBody}>
                                            <ListItemText
                                                primary={<Typography variant="body1">{table.name}</Typography>}
                                                secondary={<Typography variant="subtitle2">{table.description}</Typography>}
                                            />
                                            <ListItemText
                                                primary={<Typography variant="body1"><Span title={'Fecha de inicio: '}/>{date}</Typography>}
                                                secondary={<Typography variant="subtitle2"><Span title={'Zona horaria de la mesa: '}/>{table.timezone}</Typography>}
                                            />
                                        </Box>
                                    </ListItemButton>
                                </ListItem>
                            </Box>
                        );
                    })}
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
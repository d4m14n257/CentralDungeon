import { useRouter } from "next/router";

import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import Span from "../Span";
import ViewMoreComponent from "../ViewMoreComponent";

import { global } from '@/styles/global';

export default function ListBodyPlayer (props) {
    const { tables, info, handleTableSelect } = props;

    const router = useRouter();
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    const handleTableRoute = (id) => {
        if(info.id === 'joined-table')
            router.push(`/tables/joined/${id}`)
        else
            router.push(`/tables/available/${id}`)
    }

    return (
        <>
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
            {handleTableSelect && 
                <>
                    <Divider variant="middle" component="li" />
                    <ListItem>
                        <ListItemText primary={<ViewMoreComponent handleTableSelect={handleTableSelect} value={info}/>}/>
                    </ListItem>
                </>
            }
        </>
    );
}
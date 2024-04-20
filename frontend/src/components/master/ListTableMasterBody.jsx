import { useRouter } from 'next/router';

import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import Span from '../Span';
import ViewMoreComponent from '../ViewMoreComponent';

import { global } from '@/styles/global';

import { Message, HandlerMessage } from '../info/HandlerMessage';

export default function ListTableMasterBody (props) {
    const { tables } = props;

    const router = useRouter();
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    const handleTableSelect = () => {
        router.push('/tables');
    }

    const handlePushTable = (id) => {
        router.push(`/tables/${id}`)
    }

    return (
        <Message>
            <Message.When hasData={tables.length > 0}>
                {tables.map((table) => {
                    const startDate = new Date(table.startdate);
                    const date = startDate.toLocaleDateString('es-ES', options);

                    return (
                        <Box key={table.id}>
                            <Divider variant="middle" component="li"/>
                            <ListItem>
                                <ListItemButton
                                    onClick={() => handlePushTable(table.id)}
                                >
                                    <Box>
                                        <ListItemText 
                                            primary={<Typography variant="body1">{table.name}</Typography>}
                                            secondary={<Typography variant="subtitle2" sx={global.overText}>{table.description}</Typography>}
                                        />
                                        <ListItemText 
                                            primary={<Typography variant="body2"><Span title='Número de jugadores: '/>{table.players}</Typography>}
                                            secondary={<Typography variant="subtitle2"><Span title='Tags: '/>{table.tags ? table.tags : 'No hay tags asignados.'}</Typography>}
                                        />
                                        <ListItemText 
                                            primary={<Typography variant="body2"><Span title={'Fecha de inicio: '}/>{date}</Typography>}
                                            secondary={<Typography variant="subtitle2"><Span title={'Estado: '}/>{table.status}</Typography>}
                                        />
                                    </Box>
                                </ListItemButton>
                            </ListItem>
                        </Box>
                    );
                })}
                <Divider variant="middle" component="li" />
                <ListItem>
                    <ListItemText primary={<ViewMoreComponent handleTableSelect={handleTableSelect}/>}/>
                </ListItem>
            </Message.When>
            <Message.Else>
                <HandlerMessage message='No hay mesas creadas o en progreso por el momento.' />
            </Message.Else>
        </Message>
    );
}
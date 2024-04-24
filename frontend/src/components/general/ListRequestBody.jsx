import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import { useDate } from '@/hooks/useDate';
import { HandlerMessage, Message } from '../info/HandlerMessage';

import Span from '../Span';
import ViewMoreComponent from '../ViewMoreComponent';
import { useRouter } from 'next/router';

export default function ListRequestBody (props) {
    const { requests, isMaster } = props;
    const { handleDate } = useDate();
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push();
    }

    return  (
        <Message>
            <Message.When hasData={requests.length > 0}>
                {requests.map((item) => (
                    <Box key={item.id}>
                        <Divider variant="middle" component="li"/>
                        <ListItem>
                            <ListItemButton onClick={() => {handleTableRoute(item.id)}}>
                                <Box>
                                    <ListItemText 
                                        primary={<Typography variant="body1">{item.name}</Typography>}
                                        secondary={item.masters ? <Typography variant="subtitle2"><Span title='Masters: '/>{item.masters}</Typography> :
                                                <Typography variant="subtitle2"><Span title='Nombre de jugador: '/>{item.username}</Typography>}
                                    />
                                    <ListItemText 
                                        primary={<Typography variant="body2"><Span title='Fecha de solicitud: '/>{handleDate(item.created)}</Typography>}
                                        secondary={item.status ? <Typography variant="subtitle2"><Span title='Estado de solicitud: '/>{item.status}</Typography> : 
                                                <Typography variant="subtitle2"><Span title='Karma: '/>{item.karma}</Typography>}
                                    />
                                </Box>
                            </ListItemButton>
                        </ListItem>
                    </Box>
                ))}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message={isMaster ? 'No hay peticiones pendientes' : 'No hay solicitudes por mostrar' }/>
                </ListItem>
            </Message.Else>
        </Message>
    );
}
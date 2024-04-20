import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import Span from '../Span';
import { HandlerMessage, Message } from '../info/HandlerMessage';

export default function ListRequestBody (props) {
    const { requests, isMaster } = props;
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    return  (
        <Message>
            <Message.When hasData={requests.length > 0}>
                {requests.map((item) => {
                    const createdDate = new Date(item.created);
                    const date = createdDate.toLocaleDateString('es-ES', options);

                    return (
                        <Box key={item.id}>
                            <Divider variant="middle" component="li"/>
                            <ListItem>
                                <ListItemButton>
                                    <Box>
                                        <ListItemText 
                                            primary={<Typography variant="body1">{item.name}</Typography>}
                                            secondary={item.masters ? <Typography variant="subtitle2"><Span title='Masters: '/>{item.masters}</Typography> :
                                                      <Typography variant="subtitle2"><Span title='Nombre de jugador: '/>{item.username}</Typography>}
                                        />
                                        <ListItemText 
                                            primary={<Typography variant="body2"><Span title='Fecha de solicitud: '/>{date}</Typography>}
                                            secondary={item.status ? <Typography variant="subtitle2"><Span title='Estado de solicitud: '/>{item.status}</Typography> : 
                                                      <Typography variant="subtitle2"><Span title='Karma: '/>{item.karma}</Typography>}
                                        />
                                    </Box>
                                </ListItemButton>
                            </ListItem>
                        </Box>
                    );
                })}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message={isMaster ? 'No hay peticiones pendientes' : 'No se han creado solicitudes' }/>
                </ListItem>
            </Message.Else>
        </Message>
    );
}
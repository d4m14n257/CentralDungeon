import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import { useDate } from '@/hooks/useDate';
import { useModal } from '@/hooks/useModal';
import { HandlerMessage, Message } from '../info/HandlerMessage';

import Span from '../Span';
import RequestModal from '../player/RequestModal';

export default function ListRequestBody (props) {
    const { requests, isMaster, handleTableRoute } = props;
    const { handleDate } = useDate();
    const { open, handleOpenModalWithData, handleCloseModal, dataModal } = useModal()

    return  (
        <Message>
            <Message.When hasData={requests.length > 0}>
                {requests.map((item) => (
                    <Box key={item.id}>
                        <Divider variant="middle" component="li"/>
                        <ListItem>
                            <ListItemButton onClick={() => {
                                { item.status != 'Rejected' ? 
                                    handleTableRoute(item.table_id) :
                                    handleOpenModalWithData(item)
                                }
                            }}>
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
                {open && 
                    <RequestModal 
                        isOpen={open}
                        handleCloseModal={handleCloseModal}
                        request={dataModal}
                    />
                }
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message={isMaster ? 'No hay peticiones pendientes' : 'No hay solicitudes por mostrar' }/>
                </ListItem>
            </Message.Else>
        </Message>
    );
}
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import Span from '../Span';

export default function ListRequestMasterBody (props) {
    const { requests } = props;

    return  (
        <>
            {requests.map((item) => (
                <>
                    <Divider variant="middle" component="li"/>
                    <ListItem>
                        <ListItemButton>
                            <Box>
                                <ListItemText 
                                    primary={<Typography variant="body1">{item.name_table}</Typography>}
                                    secondary={<Typography variant="subtitle2">{item.player_name}</Typography>}
                                />
                                <ListItemText 
                                    primary={<Typography variant="body2"><Span title='Fecha de solicitud: '/>{item.date}</Typography>}
                                />
                            </Box>
                        </ListItemButton>
                    </ListItem>
                </>
            ))}
        </>
    );
}
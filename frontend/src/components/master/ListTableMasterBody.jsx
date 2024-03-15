import { useRouter } from 'next/router';

import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import Span from '../Span';
import ViewMoreComponent from '../ViewMoreComponent';

export default function ListTableMasterBody (props) {
    const { tables } = props;
    const router = useRouter();

    const handleTableSelect = () => {
        router.push('/tables');
    }

    return (
        <>
            {tables.map((item) => (
                <>
                    <Divider variant="middle" component="li"/>
                    <ListItem>
                        <ListItemButton>
                            <Box>
                                <ListItemText 
                                    primary={<Typography variant="body1">{item.name}</Typography>}
                                    secondary={<Typography variant="subtitle2">{item.description}</Typography>}
                                />
                                <ListItemText 
                                    primary={<Typography variant="body2"><Span title='Número de jugadores: '/>{item.players}</Typography>}
                                    secondary={<Typography variant="body2"><Span title='Tags: '/>{item.tag.join(', ')}</Typography>}
                                />
                            </Box>
                        </ListItemButton>
                    </ListItem>
                </>
            ))}
            <Divider variant="middle" component="li" />
            <ListItem>
                <ListItemText primary={<ViewMoreComponent handleTableSelect={handleTableSelect}/>}/>
            </ListItem>
        </>
    );
}
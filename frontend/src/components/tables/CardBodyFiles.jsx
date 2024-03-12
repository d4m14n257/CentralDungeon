import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';

import DeleteIcon from '@mui/icons-material/Delete';

import Span from '../Span';
import CardContent from '@mui/material/CardContent';

export default function CardBodyFiles(props) {
    const { files } = props;

    return (
        <>
            <CardContent>
                {files.length ? 
                    <List>
                        {files.map((file) => (
                            <>
                                <Divider variant="middle" component="li" />
                                <ListItem
                                    key={file.name}
                                    secondaryAction={
                                        <IconButton edge="end" aria-label="delete">
                                            <DeleteIcon />
                                        </IconButton>}
                                >   
                                    <ListItemText
                                        primary={<Typography variant="body1"><Span title={'Nombre del archivo: '}/>{file.name}</Typography>}
                                    />
                                </ListItem>
                            </>
                        ))}
                        <Divider variant="middle" component="li" />
                    </List> : 
                    <Typography sx={{textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h6'>
                        No hay archivos aun.
                    </Typography>
                }
            </CardContent>
        </>
    );
}
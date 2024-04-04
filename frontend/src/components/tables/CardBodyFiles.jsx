import { useContext } from 'react';

import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';

import DeleteIcon from '@mui/icons-material/Delete';

import Span from '../Span';

import { card } from '@/styles/tables/card';
import UploadButton from '@/forms/UploadButton';
import { TableStateContext } from '@/contexts/TableStateContext';

export default function CardBodyFiles(props) {
    const { files, useFiles } = props;
    const { status } = useContext(TableStateContext)

    return (
        <>
            <CardContent>
                {useFiles ?
                    files.length ? 
                        <List>
                            {files.map((file) => (
                                <>
                                    <Divider variant="middle" component="li" />
                                    <ListItem
                                        key={file.name}
                                        secondaryAction={
                                            status == 'Preparacion' ?
                                            <IconButton edge="end" aria-label="delete">
                                                <DeleteIcon />
                                            </IconButton> : <></>}
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
                        </Typography> :
                        <Typography variant='subtitle1' sx={{textAlign: 'center', opacity: '0.3', userSelect: 'none'}}>
                            No se estan usando archivos para ingresar a esta mesa.
                        </Typography>
                
                }
            </CardContent>
            {useFiles & status == 'Preparacion' ?
                <CardActions sx={card.cardActions}>
                    <UploadButton />
                </CardActions> : <></>
            }
        </>
    );
}
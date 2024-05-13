import { Snackbar, Alert, Box } from '@mui/material';
import { Error } from '../info/HandlerError';

export default function SnackMessage(props) {
    const { message, status, open, onClose, info, position } = props;

    return (
        <Snackbar 
            anchorOrigin={{ vertical: position ? 'top' : 'bottom', horizontal: position ? 'right' : 'left'}}
            open={open} 
            autoHideDuration={5000} 
            onClose={onClose}
        >   
            <Box>
                <Error>
                    <Error.When isError={ !(status >= 200 && status <= 299) }>
                        <Alert
                            onClose={onClose}
                            severity="success"
                            variant="filled"
                            sx={{ width: '100%' }}  
                        >
                            {message}
                        </Alert>
                    </Error.When>
                    <Error.When isError={ !(status >= 400 && status <= 499) }>
                        <Alert
                            onClose={onClose}
                            severity="warning"
                            variant="filled"
                            sx={{ width: '100%' }}  
                        >
                            {message} : {status}
                        </Alert>
                    </Error.When>
                    <Error.When isError={ !info }>
                        <Alert
                            onClose={onClose}
                            severity="info"
                            variant="filled"
                            sx={{ width: '100%' }}  
                        >
                            {message}
                        </Alert>
                    </Error.When>
                    <Error.Else>
                        <Alert
                            onClose={onClose}
                            severity="error"
                            variant="filled"
                            sx={{ width: '100%' }}  
                        >   
                            {status ? 
                                `${message} : ${status}` :
                                'Hubo un error inesperado'
                            }
                        </Alert>
                    </Error.Else>
                </Error>
            </Box>
        </Snackbar>
    );
}
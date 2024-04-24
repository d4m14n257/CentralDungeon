import { useRouter } from "next/router";

import { ListItemText, Divider, ListItemButton, Box, Typography, ListItem } from '@mui/material';

import Span from "../Span";

import { global } from '@/styles/global';
import { useDate } from "@/hooks/useDate";
import { Message, HandlerMessage } from "../info/HandlerMessage";
import { TABLES_AVAILABLE, TABLES_JOINED } from "@/constants/constants";

export default function ListBodyPlayer (props) {
    const { id, tables } = props;
    const { handleDate } = useDate();

    const router = useRouter();

    const handleTableRoute = (id, table_id) => {
        if(id === 'joined-tables')
            router.push(`${TABLES_JOINED}/${table_id}`)
        else
            router.push(`${TABLES_AVAILABLE}/${table_id}`)
    }

    return (
        <Message>
            <Message.When hasData={tables.length > 0}>
                {tables.map((table) => (
                    <Box key={table.name}>
                        <Divider variant="middle" component="li" />
                        <ListItem>
                            <ListItemButton onClick={() => handleTableRoute(id, table.id)}>
                                <Box sx={global.listBody}>
                                    <ListItemText
                                        primary={<Typography variant="body1">{table.name}</Typography>}
                                        secondary={<Typography variant="subtitle2">{table.description}</Typography>}
                                    />
                                    <ListItemText
                                        primary={<Typography variant="body1"><Span title={'Fecha de inicio: '}/>{handleDate(table.startdate)}</Typography>}
                                        secondary={<Typography variant="subtitle2"><Span title={'Master: '}/>{table.master ? table.master : 'No hay master asignado aun.'}</Typography>}
                                    />
                                </Box>
                            </ListItemButton>
                        </ListItem>
                    </Box>
                ))}
            </Message.When>
            <Message.Else>
                <ListItem>
                    <HandlerMessage message={id == 'joined-table' ? 'No tienes ninguna sesión en curso por el momento' : 'No hay mesas disponibles en este momento'} />
                </ListItem>
            </Message.Else>
        </Message>
    );
}
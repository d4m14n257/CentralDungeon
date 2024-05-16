import { useState, useCallback } from 'react';

import Grid from '@mui/material/Grid';

import { getter } from '@/api/getter';
import { TablesInfo } from '@/normalize/models';
import { Error, ErrorMessage } from '@/components/info/HandlerError';
import { PreparationStatus } from '@/components/tables/status/PreparationStatus';

/*
    TODO: Mesas que seas colapsables para jugadores pendientes y separar aceptadps de pendientes. 
*/

export const getServerSideProps = async (context) => {
    const { params } = context;
    const { id } = params;

    const result = await getter({ id: id, url: 'tables' });

    if(result.status == 200) {
        const data = {
            table: TablesInfo(result)
        }

        return {
            props: {
                id: id,
                ...data,
                err: false
            }
        }
    }
    else if(result.status == 203) {
        return {
            redirect: {
                destination: '/',
                permanent: false,
            },
        }
    }
    else {
        return {
            props: {
                err_result: result,
                err: true
            }
        }
    }
} 

export default function Table (props) {
    const { id, table: tableServer, err, err_result } = props;
    const [ table, setTable ] = useState(tableServer);

    const handleTableReload = useCallback(async () => {
        const result = await getter({ others: id, url: 'tables' })

        if(result.status == 200) {
            const data = {
                table: TablesInfo(result)
            }

            setTable(data.table)
        }
        else
            location.reload();

    }, [])

    return (
        <Error>
            <Error.When isError={err}>
                <Grid
                    container
                    spacing={2} 
                >
                    <PreparationStatus 
                        id={id}
                        table={table}
                        handleTableReload={handleTableReload}
                    />
                </Grid>
            </Error.When>
                <Error.Else>
                    <ErrorMessage {...err_result}/>
                </Error.Else>
            </Error>
    );
}


{/* <Grid item xs={12}>
{statusTable != 'Preparacion' ? 
    table.players.length ? 
        <TableComponent 
            title='Jugadores en mesa'
            columns={columns}
            rows={table.players}
            handleFeedback={handleFeedback}
            mt={5}
            Actions={ActionButtonMasterTable}
        /> :
        <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
            No hay jugadores registrados.
        </Typography> :
    <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
        No se pueden registrar jugadores aun.
    </Typography>
}
</Grid> */}
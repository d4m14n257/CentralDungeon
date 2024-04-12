import { useContext } from 'react';
import { useRouter } from 'next/router';

import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import ListRequestBody from '@/components/general/ListRequestBody';
import { Error, ErrorMessage } from '@/components/info/HandlerError';

import { getter } from '@/api/getter';

export const getServerSideProps = async () => {
    const result = await getter("2", 'tables/player');

    if(!result.status) {
        return {
            props: {
                public_tables: result.public_tables,
                joined_tables: result.joined_tables,
                request_tables: result.request_tables,
                err: false
            }
        }
    }
    else
        return {
            props: {
                err: result
            }
        }
}


export default function Dashboard (props) {
    const { public_tables, joined_tables, request_tables, err } = props;
    const router = useRouter();

    const handleTableSelect = ({id}) => {
        if(id) {
            if(id === 'public-tables')
                router.push(`/public-tables`)
            else if (id === 'joined-table')
                router.push(`/joined-table`)
        }
    }

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err ? true : false}>
                    <ListComponent
                        xs={12}
                        lg={8}
                        title='Mesas publicas'
                        description='Ultimas mesas disponibles para todo el publico.'
                    >   
                        <ListBodyPlayer
                            id='public-tables'
                            tables={public_tables}
                            handleTableSelect={handleTableSelect}
                        />
                    </ListComponent>
                    <ListComponent
                        xs={12}
                        lg={4}
                        title="Peticiones"
                        description="Ultimas petiiciones realizadas y su estado actual."
                    >
                        <ListRequestBody
                            requests={request_tables}
                         />
                    </ListComponent>
                    <ListComponent
                        title= 'Mesas jugando'
                        description='Mesas en las que te encuentras jugando actualmente y se encuentran activas.'
                    >
                        <ListBodyPlayer
                            id='joined-tables'
                            tables={joined_tables}
                            handleTableSelect={handleTableSelect}
                        />
                    </ListComponent>
                </Error.When>
                <Error.Else>
                    <ErrorMessage err={err}/>
                </Error.Else>
            </Error>
        </Grid>
    );
}
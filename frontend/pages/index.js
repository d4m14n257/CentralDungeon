import { useRouter } from 'next/router';

import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import ListRequestBody from '@/components/general/ListRequestBody';
import { Error, ErrorMessage } from '@/components/info/HandlerError';

import { getter } from '@/api/getter';
import { Request, Tables } from '@/normalize/models';

export const getServerSideProps = async () => {
    const result = await getter("2", 'tables/player');

    if(!result.status) {
        const data = {
            public_tables: Tables(result.public_tables),
            joined_tables: Tables(result.joined_tables),
            request_tables: Request(result.request_tables),
        }

        return {
            props: {
                ...data,
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

    const handleTableSelect = (id) => {
        console.log(id)

        if(id) {
            if(id === 'public-tables')
                router.push(`/public-tables`)
            else if (id === 'joined-tables')
                router.push(`/joined-tables`)
        }
    }

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err}>
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
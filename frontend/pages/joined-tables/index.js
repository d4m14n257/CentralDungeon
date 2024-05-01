import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { Tables } from '@/normalize/models';
import { getter } from '@/api/getter';
import { Error, ErrorMessage } from '@/components/info/HandlerError';

export const getServerSideProps = async () => {
    const result = await getter({user_id: '2', url: 'tables/joined-tables'});

    if(!result.status) {
        const data = {
            joined_tables: Tables(result.joined_tables)
        }

        return {
            props: {
                ...data,
                err: false
            }
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

export default function JoinedTable (props) {
    const { joined_tables, err, err_result } = props;

    return (
        <Grid
            container
            spacing={2}
        >   
            <Error>
                <Error.When isError={err}>
                    <ListComponent 
                        title='Mesas jugando'
                        description='Mesas en las que te encuentras jugando actualmente y se encuentran activas.'
                    >
                        <ListBodyPlayer 
                            id='joined-tables'
                            tables={joined_tables}
                        />
                    </ListComponent>
                </Error.When>
                <Error.Else>
                    <ErrorMessage {...err_result}/>
                </Error.Else>
            </Error>
        </Grid> 
    );
}
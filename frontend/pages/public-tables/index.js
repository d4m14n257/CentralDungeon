import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { getter } from '@/api/getter';
import { Tables } from '@/normalize/models';
import { Error, ErrorMessage } from '@/components/info/HandlerError';

export const getServerSideProps = async () => {
    const result = await getter({user_id: "3", url: 'tables/public-tables'});

    if(!result.status) {
        const data = {
            public_tables: Tables(result.public_tables),
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
                err_result: result,
                err: true
            }
        }
}

export default function PublicTable (props) {
    const { public_tables, err, err_result } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err}>
                    <ListComponent 
                        title='Mesas publicas'
                        description='Ultimas mesas disponibles para todo el publico.'
                    >
                        <ListBodyPlayer 
                            id='public-table'
                            tables={public_tables}
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
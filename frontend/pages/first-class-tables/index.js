import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { getter } from '@/api/getter';
import { Error, ErrorMessage } from '@/components/info/HandlerError';

export const getServerSideProps = async () => {
    const result = await getter({user_id: '3', url: 'tables/first-class-tables'});

    if(!result.status) {
        return {
            props: {
                first_class_tables: result.first_class_tables,
                err: false
            }
        }
    }
    else
        return {
            props: {
                err_result: result,
                err: true,
            }
        }
}

export default function FirstClaseTable (props) {
    const { first_class_tables, err, err_result } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err}>
                    <ListComponent 
                        title='Mesas de temporada'
                        description='Son las mesas creadas.'
                    >
                        <ListBodyPlayer 
                            info={{name: 'Mis de primer clase', id: 'first-class-table'}}
                            tables={first_class_tables}
                        />
                    </ListComponent>
                </Error.When>
                <Error.Else>
                    <ErrorMessage {...err_result} />
                </Error.Else>
            </Error>
        </Grid> 
    );
}
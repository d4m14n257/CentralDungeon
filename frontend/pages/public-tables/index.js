import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { getter } from '@/api/getter';

export const getServerSideProps = async () => {
    const result = await getter("3", 'tables/public-tables');

    if(!result.status) {
        return {
            props: {
                public_tables: result.public_tables,
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

//TODO: implement infinity scroll when I gonna push tables.

export default function PublicTable (props) {
    const { public_tables } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <ListComponent 
                title='Mesas publicas'
                description='Ultimas mesas disponibles para todo el publico.'
            >
                <ListBodyPlayer 
                    id='public-table'
                    tables={public_tables}
                />
            </ListComponent>
        </Grid> 
    );
}
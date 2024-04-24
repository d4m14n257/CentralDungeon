import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { Tables } from '@/normalize/models';
import { getter } from '@/api/getter';

export const getServerSideProps = async () => {
    const result = await getter('2', 'tables/joined-tables');

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
                err: result
            }
        }
    }
}

export default function JoinedTable (props) {
    const { joined_tables } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <ListComponent 
                title='Mesas jugando'
                description='Mesas en las que te encuentras jugando actualmente y se encuentran activas.'
            >
                <ListBodyPlayer 
                    id='joined-tables'
                    tables={joined_tables}
                />
            </ListComponent>
        </Grid> 
    );
}
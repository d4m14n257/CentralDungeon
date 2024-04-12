import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';
import ListBodyPlayer from '@/components/general/ListBodyPlayer';
import { getter } from '@/api/getter';

export const getServerSideProps = async () => {
    const result = await getter('3', 'tables/first-class-tables');

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
                err: result
            }
        }
}

export default function FirstClaseTable (props) {
    const { first_class_tables } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <ListComponent 
                title='Mesas de temporada'
                description='Son las mesas creadas.'
            >
                <ListBodyPlayer 
                    info={{name: 'Mis de primer clase', id: 'first-class-table'}}
                    tables={first_class_tables}
                />
            </ListComponent>
        </Grid> 
    );
}
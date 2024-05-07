import Typography from '@mui/material/Typography';

import Span from '../Span';
import { useDate } from '@/hooks/useDate';
import { global } from '@/styles/global';

export default function CardContentTable (props) {
    const { table } = props;
    const { handleDate } = useDate();

    return (
        <>
            <Typography sx={global.overText}>
                <Span title={'Descripción: '} />
                {table.description ? table.description : 'No se ha agregado una descripción.'}
            </Typography>
            <Typography  sx={global.overText}>
                <Span title={'Reglas: '} />
                {table.permitted ? table.permitted : 'No se han agregado reglas u cosas permitidas.'}
            </Typography>
            <Typography  sx={global.overText}>
                <Span title={'Fecha de inicio: '} />
                {table.startdate ? handleDate(table.startdate) : 'No se a dado una fecha de inicio de la mesa.'}
            </Typography>
            <Typography  sx={global.overText}>
                <Span title={'Zona horaria de la mesa: '}/>
                {table.timezone ? table.timezone : 'No se ha definido su zona horaria, es requerida.'}
            </Typography>
            <Typography  sx={global.overText}>
                <Span title={'Duracion por sesión: '} />
                {table.duration ? `${table.duration} horas` : 'No se ha definido un tiempo de duración.'}
            </Typography>
            <Typography  sx={global.overText}>
                <Span title={'Estado: '} />
                {table.status}
            </Typography>
        </>
    );
}
import { useRef } from 'react';

import Typography from '@mui/material/Typography';

import Span from '../Span';

export default function CardContentTable (props) {
    const { table, haveSystem } = props;

    const startDate = new Date(table.startdate);
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    const date = useRef(startDate.toLocaleDateString('es-ES', options));

    return (
        <>
            <Typography>
                    <Span title={'Descripción: '} />
                    {table.descripction}
                </Typography>
                <Typography>
                    <Span title={'Detalles: '} />
                    {table.allowsmaster}
                </Typography>
                <Typography>
                    <Span title={'Tags: '} /> 
                    {table.tag ? 
                        table.tag.join(', ')         
                        : "No hay tags asignados"
                    }
                </Typography>
                <Typography>
                    <Span title={'Fecha de inicio: '} />
                    {date.current} UTC-06:00
                </Typography>
                <Typography>
                    <Span title={'Zona horaria de la mesa: '}/>
                    {table.timezone}
                </Typography>
                <Typography>
                    <Span title={'Horario: '} />
                    {table.schedule.join(', ')}
                </Typography>
                {haveSystem && 
                    <Typography>
                        <Span title={'Sistema: '} />
                        {table.system}
                    </Typography>
                }
                <Typography>
                    <Span title={'Plataforma: '} />
                    {table.platform}
                </Typography>
                <Typography>
                    <Span title={'Duracion por sesión: '} />
                    {table.duration}
                </Typography>
                <Typography>
                    <Span title={'Estado: '} />
                    {table.status}
                </Typography>
        </>
    );
}
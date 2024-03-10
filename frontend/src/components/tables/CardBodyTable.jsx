import { useEffect, useRef, useState } from 'react';

import Typography from '@mui/material/Typography';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import RequerimentsModalTable from './RequerimentsModalTable'; 

import RequestModalTable from './RequestModalTable';
import Span from '../Span';

import { card } from '@/styles/tables/card';

export default function CardBodyTable (props) {
    const { details } = props;

    const startDate = new Date(details.startdate);
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };

    const date = useRef(startDate.toLocaleDateString('es-ES', options));

    const [openRequerimentsModal, setOpenRequerimentsModal] = useState(false);
    const [openRequestModal, setOpenRequestModal] = useState(false);

    const handleDownloadFiles = () => {
        details.files.map((file) => {
            const link = document.createElement('a');
            link.href = file;
            link.download = "CV-Jorge-Damian-Dominguez-Jimenez-English.pdf"; 
            link.click();
        })
    }

    const handleOpenRequerimentsModal = () => {
        setOpenRequerimentsModal(true);
    }

    const handleCloseRequerimentsModal = () => {
        setOpenRequerimentsModal(false);
    }

    const handleOpenRequestModal = () => {
        setOpenRequestModal(true);
    }

    const handleCloseRequestModal = () => {
        setOpenRequestModal(false);
    }

    return (
        <>
            <CardContent>
                <Typography>
                    <Span title={'Descripción: '} />
                    {details.descripction}
                </Typography>
                <Typography>
                    <Span title={'Detalles: '} />
                    {details.allowsmaster}
                </Typography>
                <Typography>
                    <Span title={'Tags: '} /> 
                    {details.tag ? 
                        details.tag.join(', ')         
                        : "No hay tags asignados"
                    }
                </Typography>
                <Typography>
                    <Span title={'Fecha de inicio: '} />
                    {date.current} UTC-06:00
                </Typography>
                <Typography>
                    <Span title={'Zona horaria de la mesa: '}/>
                    {details.timezone}
                </Typography>
                <Typography>
                    <Span title={'Horario: '} />
                    {details.schedule.join(', ')}
                </Typography>
                <Typography>
                    <Span title={'Estado: '} />
                    {details.status}
                </Typography>
            </CardContent>
            <CardActions sx={card.cardActions}>
                <ButtonGroup>
                    <Button onClick={handleOpenRequerimentsModal}>Requisitos</Button>
                    <Button onClick={handleOpenRequestModal}>Solicitar</Button>
                    <Button onClick={handleDownloadFiles}>Descargar</Button>
                </ButtonGroup>
            </CardActions>
            <RequerimentsModalTable 
                isOpen={openRequerimentsModal}
                handleCloseModal={handleCloseRequerimentsModal}
                handleOpenRequestModal={handleOpenRequestModal}
                requeriments={details.requeriments}
            />
            {openRequestModal && 
                <RequestModalTable 
                    isOpen={openRequestModal}
                    handleCloseModal={handleCloseRequestModal}
                />
            }
        </>
    );
}
import { useRef, useContext } from 'react';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import { styled } from '@mui/material/styles';

import CloudUploadIcon from '@mui/icons-material/CloudUpload';

import { Editor } from "@tinymce/tinymce-react";

import ModalBase from "../ModalBase";

import { modal } from '@/styles/tables/modal';

import { ColorModeContext } from '@/context/ColorModeContext';

const VisuallyHiddenInput = styled('input')({
    clip: 'rect(0 0 0 0)',
    clipPath: 'inset(50%)',
    height: 1,
    overflow: 'hidden',
    position: 'absolute',
    bottom: 0,
    left: 0,
    whiteSpace: 'nowrap',
    width: 1,
});

export default function RequestModalTable (props) {
    const {isOpen, handleCloseModal} = props;
    const { mode } = useContext(ColorModeContext)
    const editorRef = useRef(null);

    // const log = () => {
    //     if (editorRef.current) {
    //         console.log(editorRef.current.getContent());
    //     }
    // };

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Solicitud
                    </Typography>
                </Box>
                <Box sx={modal.content}>
                    <Editor
                        apiKey={process.env.NEXT_PUBLIC_TINY_KEY}
                        onInit={(evt, editor) => (editorRef.current = editor)}
                        init={{
                            skin: mode == 'dark' ? 'oxide-dark' : 'oxide',
                            content_css: mode == 'dark' ? 'dark' : 'default',
                            height: 400,
                            menubar: false,
                            toolbar:
                                "undo redo | blocks | " +
                                "bold italic forecolor | alignleft aligncenter " +
                                "alignright alignjustify | bullist numlist outdent indent | " +
                                "removeformat",
                            content_style:
                                "body { font-family:Helvetica,Arial,sans-serif; font-size:14px }",
                            }}
                    />
                    <Button
                        component="label"
                        role={undefined}
                        variant="contained"
                        tabIndex={-1}
                        startIcon={<CloudUploadIcon />}
                    >
                        Upload file
                        <VisuallyHiddenInput type="file" />
                    </Button>
                </Box>
                <Box sx={modal.footer}>
                    <Button variant='outlined' >Subir</Button>
                </Box>
            </Box>
        </ModalBase>
    );
}